package com.github.madbrain.jschema;

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder;
import org.jetbrains.annotations.NotNull;

import static com.github.madbrain.jschema.MessageLibConstants.GET_STRING_METHOD_NAME;
import static com.github.madbrain.jschema.MessageLibConstants.MESSAGE_CLASS_NAME;

public class ExtractorSpecAnnotator implements Annotator {

    private static final Logger LOG = Logger.getInstance(ExtractorSpecAnnotator.class);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiLiteralExpression)) return;
        if (!(element.getParent() instanceof PsiExpressionList)) return;
        if (!(element.getParent().getParent() instanceof PsiMethodCallExpression)) return;

        PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element.getParent().getParent();
        PsiReferenceExpression methodExpression = methodCall.getMethodExpression();

        if (methodExpression.getReferenceName() == null
                || !methodExpression.getReferenceName().equals(GET_STRING_METHOD_NAME)) return;

        PsiMethod method = methodCall.resolveMethod();

        if (method == null || method.getContainingClass() == null) return;

        final PsiClass containingClass = method.getContainingClass();
        final Project project = element.getProject();
        final PsiClass messageClass = JavaPsiFacade.getInstance(project)
                .findClass(MESSAGE_CLASS_NAME, GlobalSearchScope.allScope(project));
        if (messageClass == null
                || !(containingClass.equals(messageClass) || containingClass.isInheritor(messageClass, true)))
            return;

        PsiExpression e = methodCall.getMethodExpression().getQualifierExpression();

        if (e == null
                || e.getType() == null
                || !(e.getType() instanceof PsiClassType))
            return;

        PsiType argType = ((PsiClassType) e.getType()).getParameters()[0];
        if (!(argType instanceof PsiClassType)) return;

        String schemaFilePath = ClassSchemaIndex.findSchemaBoundToClass(project,
                ((PsiClassType) argType).resolve().getQualifiedName());

        if (schemaFilePath == null) return;

        final JsonSchemaService jsonSchemaService = JsonSchemaService.Impl.get(project);
        Module module = ModuleUtil.findModuleForPsiElement(element);

        VirtualFile schemaFile = getFile(module, schemaFilePath);
        if (schemaFile == null || !jsonSchemaService.isSchemaFile(schemaFile)) return;

        JsonSchemaObject schemaObject = jsonSchemaService.getSchemaObjectForSchemaFile(schemaFile);

        PsiLiteralExpression literalExpression = (PsiLiteralExpression) element;
        String value = literalExpression.getValue() instanceof String ? (String) literalExpression.getValue() : null;

        // TODO JsonPointerPosition n'est pas assez précis pour faire un reporting du segment en erreur
        JsonPointerPosition pointer = JsonPointerPosition.parsePointer(value);

        String message = evaluate(schemaObject, pointer);

        if (message != null) {
            holder.createErrorAnnotation(literalExpression, message);
        }
    }

    private String evaluate(JsonSchemaObject schemaObject, JsonPointerPosition pointer) {
        String message = null;
        do {
            Pair<ThreeState, JsonSchemaObject> step = JsonSchemaVariantsTreeBuilder.doSingleStep(pointer, schemaObject, true);
            if (step.first == ThreeState.NO) {
                message = "Unknown property " + pointer.getFirstName();
                break;
            }
            pointer = pointer.skip(1);
            schemaObject = step.second;
        } while (pointer.size() > 0 && schemaObject != null);

        if (message == null && schemaObject != null && schemaObject.getType() != JsonSchemaType._string) {
            message = "Property not a string";
        }
        return message;
    }

    public static VirtualFile getFile(Module module, String schemaFilePath) {
        // TODO also look in classes
        for (VirtualFile contentRoot : OrderEnumerator.orderEntries(module).recursively().productionOnly().sources().getRoots()) {
            VirtualFile schemaFile = contentRoot.findFileByRelativePath(schemaFilePath);
            if (schemaFile != null) {
                return schemaFile;
            }
        }
        return null;
    }
}
