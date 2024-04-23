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

package com.demonwav.mcdev.platform.mixin.reference.selector

import com.demonwav.mcdev.platform.mixin.handlers.injectionPoint.TargetHandlerResolver
import com.demonwav.mcdev.platform.mixin.reference.MethodVariantCollector
import com.demonwav.mcdev.platform.mixin.reference.MixinReference
import com.demonwav.mcdev.platform.mixin.util.MixinConstants
import com.demonwav.mcdev.platform.mixin.util.findSourceClass
import com.demonwav.mcdev.platform.mixin.util.findSourceElement
import com.demonwav.mcdev.platform.mixin.util.getPossiblePrefixes
import com.demonwav.mcdev.util.constantStringValue
import com.demonwav.mcdev.util.insideAnnotationAttribute
import com.demonwav.mcdev.util.reference.ReferenceResolver
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteral
import com.intellij.psi.util.parentOfType

abstract class AbstractTargetHandlerReference : ReferenceResolver(), MixinReference {
    override val description: String
        get() = "target reference '%s'"

    override fun isUnresolved(context: PsiElement): Boolean {
        return resolveNavigationTargets(context) == null
    }

    override fun isValidAnnotation(name: String, project: Project) = name == MixinConstants.MixinSquared.TARGET_HANDLER

    abstract fun resolveNavigationTargets(context: PsiElement): Array<PsiElement>?

    override fun resolveReference(context: PsiElement): PsiElement? {
        return resolveNavigationTargets(context)?.firstOrNull()
    }

    override fun collectVariants(context: PsiElement): Array<Any> {
        return emptyArray()
    }
}

object TargetHandlerMixinReference : AbstractTargetHandlerReference() {
    val ELEMENT_PATTERN: ElementPattern<PsiLiteral> = PsiJavaPatterns.psiLiteral(StandardPatterns.string())
        .insideAnnotationAttribute(MixinConstants.MixinSquared.TARGET_HANDLER, "mixin")

    override fun resolveNavigationTargets(context: PsiElement): Array<PsiElement>? {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return null
        val resolver = TargetHandlerResolver(targetHandler)
        val target = resolver.resolveMixinTarget() ?: return null
        return target.findSourceClass(context.project, context.resolveScope, canDecompile = true)?.let { arrayOf(it) }
    }
}

object TargetHandlerNameReference : AbstractTargetHandlerReference(), MethodVariantCollector {
    val ELEMENT_PATTERN: ElementPattern<PsiLiteral> = PsiJavaPatterns.psiLiteral(StandardPatterns.string())
        .insideAnnotationAttribute(MixinConstants.MixinSquared.TARGET_HANDLER, "name")

    override fun resolveNavigationTargets(context: PsiElement): Array<PsiElement>? {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return null
        val resolver = TargetHandlerResolver(targetHandler)
        val target = resolver.resolveMixinTarget() ?: return null
        val targetMethods = resolver.resolveNameTargets(target) ?: return null
        return targetMethods.mapNotNull {
            it.findSourceElement(
                target,
                context.project,
                context.resolveScope,
                canDecompile = true
            )
        }.toTypedArray()
    }

    override fun collectVariants(context: PsiElement): Array<Any> {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return emptyArray()
        val resolver = TargetHandlerResolver(targetHandler)
        val target = resolver.resolveMixinTarget() ?: return emptyArray()
        return collectVariants(context, target)
    }

    override val requireDescriptor = false
}

object TargetHandlerPrefixReference : AbstractTargetHandlerReference() {
    val ELEMENT_PATTERN: ElementPattern<PsiLiteral> = PsiJavaPatterns.psiLiteral(StandardPatterns.string())
        .insideAnnotationAttribute(MixinConstants.MixinSquared.TARGET_HANDLER, "prefix")

    override fun resolveNavigationTargets(context: PsiElement): Array<PsiElement>? {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return null
        val prefix = targetHandler.findAttributeValue("prefix")?.constantStringValue ?: return null
        val resolver = TargetHandlerResolver(targetHandler)
        val targetAnnotations = resolver.resolvePrefixTargets(prefix) ?: return null
        return targetAnnotations.toTypedArray()
    }

    override fun collectVariants(context: PsiElement): Array<Any> {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return emptyArray()
        val prefix = targetHandler.findAttributeValue("prefix")?.constantStringValue ?: return emptyArray()
        return getPossiblePrefixes().filter { it.startsWith(prefix) }.toTypedArray()
    }
}
