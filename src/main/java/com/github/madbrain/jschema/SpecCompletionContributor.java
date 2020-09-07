package com.github.madbrain.jschema;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.github.madbrain.jschema.ExtractorSpecAnnotator.getFile;
import static com.github.madbrain.jschema.MessageLibConstants.GET_STRING_METHOD_NAME;
import static com.github.madbrain.jschema.MessageLibConstants.MESSAGE_CLASS_NAME;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiLiteral;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;

public class SpecCompletionContributor extends CompletionContributor {

  public SpecCompletionContributor() {
    extend(CompletionType.BASIC, psiElement(JavaTokenType.STRING_LITERAL).withParent(
            psiLiteral().methodCallParameter(
                    0,
                    psiMethod()
                            .withName(GET_STRING_METHOD_NAME)
                            .definedInClass(MESSAGE_CLASS_NAME))),
            new CompletionProvider<CompletionParameters>() {
              public void addCompletions(@NotNull CompletionParameters parameters,
                                         @NotNull ProcessingContext context,
                                         @NotNull CompletionResultSet resultSet) {
                  PsiElement element = parameters.getPosition().getParent();
                  PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element.getParent().getParent();
                  final Project project = element.getProject();
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

                  suggestions(schemaObject, pointer).forEach(suggestion -> {
                      resultSet.addElement(LookupElementBuilder.create(suggestion));
                  });
              }
            }
    );
  }

    private Set<String> suggestions(JsonSchemaObject schemaObject, JsonPointerPosition pointer) {
        Set<String> suggestions = null;
        do {
            Pair<ThreeState, JsonSchemaObject> step = JsonSchemaVariantsTreeBuilder.doSingleStep(pointer, schemaObject, true);
            if (step.first == ThreeState.NO) {
                suggestions = schemaObject.getProperties().keySet();
                break;
            }
            pointer = pointer.skip(1);
            schemaObject = step.second;
        } while (pointer.size() > 0 && schemaObject != null);

        if (suggestions == null && schemaObject != null && schemaObject.getType() != JsonSchemaType._string) {
            suggestions = new HashSet<>();
        }
        return suggestions;
    }

}