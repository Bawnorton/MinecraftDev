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

package com.demonwav.mcdev.platform.mixin.inspection.mixinsquared

import com.demonwav.mcdev.platform.mixin.handlers.injectionPoint.TargetHandlerResolver
import com.demonwav.mcdev.platform.mixin.inspection.MixinInspection
import com.demonwav.mcdev.platform.mixin.util.MixinConstants
import com.demonwav.mcdev.platform.mixin.util.getSimpleAnnotationName
import com.demonwav.mcdev.platform.mixin.util.isPrefix
import com.demonwav.mcdev.util.constantStringValue
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElementVisitor

class InvalidTargetHandlerPrefixInspection : MixinInspection() {
    override fun getStaticDescription() = "Reports unresolved mixin references in TargetHandler annotations"

    override fun buildVisitor(holder: ProblemsHolder): PsiElementVisitor = object : JavaElementVisitor() {
        override fun visitAnnotation(targetHandlerAnnotation: PsiAnnotation) {
            if (targetHandlerAnnotation.qualifiedName != MixinConstants.MixinSquared.TARGET_HANDLER) {
                return
            }

            val attribute = targetHandlerAnnotation.findDeclaredAttributeValue("prefix") ?: return
            val prefix = attribute.constantStringValue ?: return
            if (isPrefix(prefix)) {
                val annotations = TargetHandlerResolver(targetHandlerAnnotation).resolvePrefixTargets(prefix)
                if (annotations.isNullOrEmpty()) {
                    val simpleName = getSimpleAnnotationName(prefix)
                    holder.registerProblem(
                        attribute,
                        "Could not find any '$simpleName' annotations"
                    )
                }
                return
            }

            holder.registerProblem(
                attribute,
                "Invalid prefix"
            )
        }
    }
}
