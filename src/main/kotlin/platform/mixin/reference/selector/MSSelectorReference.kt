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

import com.demonwav.mcdev.platform.mixin.reference.MixinReference
import com.demonwav.mcdev.platform.mixin.util.MixinConstants
import com.demonwav.mcdev.platform.mixin.util.bytecode
import com.demonwav.mcdev.platform.mixin.util.findSourceClass
import com.demonwav.mcdev.util.constantStringValue
import com.demonwav.mcdev.util.insideAnnotationAttribute
import com.demonwav.mcdev.util.reference.ReferenceResolver
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteral
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parentOfType
import org.objectweb.asm.tree.ClassNode

object MSSelectorReference : ReferenceResolver(), MixinReference {
    val ELEMENT_PATTERN: ElementPattern<PsiLiteral> = PsiJavaPatterns.psiLiteral(StandardPatterns.string())
        .insideAnnotationAttribute(MixinConstants.MixinSquared.TARGET_HANDLER, "mixin")

    override val description: String
        get() = "target reference '%s'"

    override fun isUnresolved(context: PsiElement): Boolean {
        return resolveNavigationTargets(context) == null
    }

    override fun isValidAnnotation(name: String, project: Project) = name == MixinConstants.MixinSquared.TARGET_HANDLER

    fun resolveNavigationTargets(context: PsiElement): Array<PsiElement>? {
        val targetHandler = context.parentOfType<PsiAnnotation>() ?: return null
        val target = getMixinTarget(targetHandler) ?: return null
        return target.findSourceClass(context.project, context.resolveScope, canDecompile = true)?.let { arrayOf(it) }
    }

    fun getMixinTarget(targetHandler: PsiAnnotation): ClassNode? {
        val project = targetHandler.project
        val targetMixinClass = CachedValuesManager.getManager(project).getCachedValue(project) {
            val className = targetHandler.findAttributeValue("mixin")?.constantStringValue
                ?: return@getCachedValue CachedValueProvider.Result(null, PsiModificationTracker.MODIFICATION_COUNT)
            val result = JavaPsiFacade.getInstance(project).findClass(className, targetHandler.resolveScope)

            CachedValueProvider.Result(result, PsiModificationTracker.MODIFICATION_COUNT)
        }
        return targetMixinClass?.bytecode
    }

    override fun resolveReference(context: PsiElement): PsiElement? {
        return resolveNavigationTargets(context)?.firstOrNull()
    }

    override fun collectVariants(context: PsiElement): Array<Any> {
        return emptyArray()
    }
}
