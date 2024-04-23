/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2024 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, version 3.0 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.demonwav.mcdev.platform.mixin.reference

import com.demonwav.mcdev.platform.mixin.handlers.MixinAnnotationHandler
import com.demonwav.mcdev.platform.mixin.reference.target.TargetReference
import com.demonwav.mcdev.platform.mixin.util.ClassAndMethodNode
import com.demonwav.mcdev.platform.mixin.util.bytecode
import com.demonwav.mcdev.platform.mixin.util.findMethods
import com.demonwav.mcdev.platform.mixin.util.findSourceElement
import com.demonwav.mcdev.platform.mixin.util.findUpstreamMixin
import com.demonwav.mcdev.platform.mixin.util.mixinTargets
import com.demonwav.mcdev.util.MemberReference
import com.demonwav.mcdev.util.constantStringValue
import com.demonwav.mcdev.util.findContainingClass
import com.demonwav.mcdev.util.findContainingMethod
import com.demonwav.mcdev.util.reference.PolyReferenceResolver
import com.demonwav.mcdev.util.toResolveResults
import com.demonwav.mcdev.util.toTypedArray
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteral
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.parentOfType
import com.intellij.util.ArrayUtil
import org.objectweb.asm.tree.ClassNode

/**
 * The reference inside e.g. @Inject.method(). Similar to [TargetReference], this reference has different ways of being
 * resolved. See the docs for that class for details.
 */
abstract class AbstractMethodReference : PolyReferenceResolver(), MixinReference, MethodVariantCollector {
    abstract fun parseSelector(context: PsiElement): MixinSelector?

    open fun parseSelector(stringValue: String, context: PsiElement): MixinSelector? {
        return parseSelector(context)
    }

    protected open fun getTargets(context: PsiElement): Collection<ClassNode>? {
        val psiClass = context.findContainingClass() ?: return null
        val targets = psiClass.mixinTargets
        val upstreamMixin = context.findContainingMethod()?.findUpstreamMixin()?.bytecode
        return when {
            upstreamMixin != null -> targets + upstreamMixin
            else -> targets
        }
    }

    override fun isUnresolved(context: PsiElement): Boolean {
        // check if the annotation handler is soft
        val annotationQName = context.parentOfType<PsiAnnotation>()?.qualifiedName
        if (annotationQName != null &&
            MixinAnnotationHandler.forMixinAnnotation(annotationQName, context.project)?.isSoft == true
        ) {
            return false
        }

        val stringValue = context.constantStringValue ?: return false
        val targetMethodInfo = parseSelector(stringValue, context) ?: return false
        val targets = getTargets(context) ?: return false
        return !targets.asSequence().flatMap {
            var actualTarget = it
            if (targetMethodInfo is DynamicMixinSelector) {
                actualTarget = targetMethodInfo.redirectOwner(actualTarget)
            }
            actualTarget.findMethods(targetMethodInfo)
        }.any()
    }

    fun getReferenceIfAmbiguous(context: PsiElement): MemberReference? {
        val targetReference = parseSelector(context) as? MemberReference ?: return null
        if (targetReference.descriptor != null) {
            return null
        }

        val targets = getTargets(context) ?: return null
        return if (isAmbiguous(targets, targetReference)) targetReference else null
    }

    private fun isAmbiguous(targets: Collection<ClassNode>, targetReference: MemberReference): Boolean {
        if (targetReference.matchAllNames) {
            return targets.any {
                val methods = it.methods
                methods != null && methods.size > 1
            }
        }
        return targets.any { it.findMethods(MemberReference(targetReference.name)).count() > 1 }
    }

    private fun resolve(context: PsiElement): Sequence<ClassAndMethodNode>? {
        val targets = getTargets(context) ?: return null
        val targetedMethods = when (context) {
            is PsiLiteral -> context.constantStringValue?.let { listOf(it) } ?: emptyList()
            is PsiArrayInitializerMemberValue -> context.initializers.mapNotNull { it.constantStringValue }
            else -> emptyList()
        }

        return targetedMethods.asSequence().flatMap { method ->
            val targetReference = parseSelector(method, context) ?: return@flatMap emptySequence()
            return@flatMap resolve(targets, targetReference)
        }
    }

    private fun resolve(
        targets: Collection<ClassNode>,
        selector: MixinSelector,
    ): Sequence<ClassAndMethodNode> {
        return targets.asSequence()
            .flatMap { target: ClassNode ->
                var actualTarget = target
                if (selector is DynamicMixinSelector) {
                    actualTarget = selector.redirectOwner(actualTarget)
                }
                val methods = actualTarget.findMethods(selector)
                methods.map { ClassAndMethodNode(actualTarget, it) }
            }
    }

    fun resolveIfUnique(context: PsiElement): ClassAndMethodNode? {
        return resolve(context)?.singleOrNull()
    }

    fun resolveAllIfNotAmbiguous(context: PsiElement): List<ClassAndMethodNode>? {
        val targets = getTargets(context) ?: return null

        val targetedMethods = when (context) {
            is PsiLiteral -> context.constantStringValue?.let { listOf(it) } ?: emptyList()
            is PsiArrayInitializerMemberValue -> context.initializers.mapNotNull { it.constantStringValue }
            else -> emptyList()
        }

        return targetedMethods.asSequence().flatMap { method ->
            val targetReference = parseSelector(method, context) ?: return@flatMap emptySequence()
            if (targetReference is MemberReference && targetReference.descriptor == null && isAmbiguous(
                    targets,
                    targetReference,
                )
            ) {
                return@flatMap emptySequence()
            }
            return@flatMap resolve(targets, targetReference)
        }.toList()
    }

    fun resolveForNavigation(context: PsiElement): Array<PsiElement>? {
        return resolve(context)?.mapNotNull {
            it.method.findSourceElement(
                it.clazz,
                context.project,
                scope = context.resolveScope,
                canDecompile = true,
            )
        }?.toTypedArray()
    }

    override fun resolveReference(context: PsiElement): Array<ResolveResult> {
        return resolve(context)?.mapNotNull {
            it.method.findSourceElement(
                it.clazz,
                context.project,
                scope = context.resolveScope,
                canDecompile = false,
            )
        }?.toResolveResults() ?: ResolveResult.EMPTY_ARRAY
    }

    override fun collectVariants(context: PsiElement): Array<Any> {
        val targets = getTargets(context) ?: return ArrayUtil.EMPTY_OBJECT_ARRAY
        return targets.singleOrNull()?.let { collectVariants(context, it) } ?: collectVariants(context, targets)
    }

    override val requireDescriptor = false
}
