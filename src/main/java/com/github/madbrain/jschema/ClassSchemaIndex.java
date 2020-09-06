package com.github.madbrain.jschema;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.madbrain.jschema.MessageLibConstants.SCHEMA_FILE_CLASS_NAME;

public class ClassSchemaIndex extends FileBasedIndexExtension<String, String> {

    @NonNls
    public static final ID<String, String> NAME = ID.create("com.github.madbrain.jschema.ClassSchemaIndex");

    @NotNull
    @Override
    public ID<String, String> getName() {
        return NAME;
    }

    @Override
    public int getVersion() {
        return 2;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return new DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE) {
            @Override
            public boolean acceptInput(@NotNull VirtualFile file) {
                return super.acceptInput(file) && JavaFileElementType.isInSourceContent(file);
            }
        };
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<String> getValueExternalizer() {
        return new DataExternalizer<String>() {
            @Override
            public void save(@NotNull DataOutput out, String value) throws IOException {
                EnumeratorStringDescriptor.INSTANCE.save(out, value);
            }

            @Override
            public String read(@NotNull DataInput in) throws IOException {
                return EnumeratorStringDescriptor.INSTANCE.read(in);
            }
        };
    }

    @NotNull
    @Override
    public DataIndexer<String, String, FileContent> getIndexer() {
        return inputData -> {
            Map<String, String> result = new HashMap<>();
            PsiJavaFile file = (PsiJavaFile) inputData.getPsiFile();
            for (PsiClass cls : file.getClasses()) {
                PsiAnnotation ann = cls.getAnnotation(SCHEMA_FILE_CLASS_NAME);
                if (ann != null) {
                    PsiAnnotationMemberValue value = ann.findAttributeValue("value");
                    if (value instanceof PsiLiteralExpression) {
                        PsiLiteralExpression literalValue = (PsiLiteralExpression) value;
                        if (literalValue.getValue() instanceof String) {
                            result.put(cls.getQualifiedName(), (String) literalValue.getValue());
                        }
                    }
                }
            }
            return result;
        };
    }

    public static String findSchemaBoundToClass(Project project, String className) {
        return findSchemaBoundToClass(project, className, ProjectScope.getAllScope(project));
    }

    public static String findSchemaBoundToClass(final Project project,
                                                final String className,
                                                final GlobalSearchScope scope) {
        return ReadAction.compute(() -> {
            try {
                List<String> values = FileBasedIndex.getInstance().getValues(NAME, className,
                        GlobalSearchScope.projectScope(project).intersectWith(scope));
                if (values.size() != 1) return null;
                return values.get(0);
            } catch (IndexNotReadyException e) {
                return null;
            }

        });
    }
}
