package codes.mettlach;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.code.Type.JCPrimitiveType;
import com.sun.tools.javac.model.FilteredMemberList;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import static javax.lang.model.util.ElementFilter.methodsIn;

@SupportedAnnotationTypes("codes.mettlach.Presenter")
public class JavaDraperBuildProcessor extends AbstractProcessor {
    private JavacElements elementUtils;
    private TreeMaker treeMaker;
    private JavacTrees trees;
    private JavacTypes types;
    private Names names;

    class DraperVisitor extends TreeTranslator {
        public void visitMethodDef(JCMethodDecl methodDecl) {
            System.out.println(methodDecl.getName());
        }
    }

    class UnknownExpressionType extends Exception {
        public UnknownExpressionType(String message) {
            super(message);
        }
    }

    @Override
    public void init(final ProcessingEnvironment procEnv) {
        super.init(procEnv);
        JavacProcessingEnvironment javacProcessingEnv = (JavacProcessingEnvironment) procEnv;
        Context context = javacProcessingEnv.getContext();

        this.elementUtils = javacProcessingEnv.getElementUtils();
        this.treeMaker = TreeMaker.instance(context);
        this.trees = JavacTrees.instance(this.processingEnv);
        this.types = JavacTypes.instance(context);
        this.names = Names.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnvironment) {
        for (TypeElement annotation : annotations) {
            // Obtain all instances annotated by the annotation.
            Set<? extends Element> annotatedElements = roundEnvironment.getElementsAnnotatedWith(annotation);

            for (Element element : annotatedElements) {
                Presenter presenterAnnotation = element.getAnnotation(Presenter.class);
                String instanceName = presenterAnnotation.instanceName();
                JCClassDecl presenterClassTree = trees.getTree((TypeElement) element);
                Optional<TypeElement> maybeTypeElement = Optional.empty();

                try {
                    presenterAnnotation.value();
                } catch (MirroredTypeException e) {
                    maybeTypeElement = getProcessorTargetTypeElement(e.getTypeMirror());
                }

                if (maybeTypeElement.isEmpty()) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Invalid value provided to @Presenter annotation. Must be of type Class.",
                            element);

                    return false;
                }

                TypeElement annotationTarget = maybeTypeElement.get();
                FilteredMemberList targetClassMembers = elementUtils.getAllMembers(annotationTarget);
                List<ExecutableElement> methodElements = methodsIn(targetClassMembers);
                List<ExecutableElement> methodsToProxy = filterOutNonProxyMethods(methodElements);

                try {
                    for (ExecutableElement methodElement : methodsToProxy) {
                        treeMaker.at(presenterClassTree.pos);
                        JCMethodDecl methodDecl = buildMethodDecl(methodElement, instanceName);
                        System.out.println(methodDecl.getName());
                        presenterClassTree.defs = presenterClassTree.defs.append(methodDecl);
                    }
                } catch (UnknownExpressionType e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), element);
                    return false;
                }
            }
        }

        return true;
    }

    private List<ExecutableElement> filterOutNonProxyMethods(List<ExecutableElement> methodElements) {
        return methodElements.stream().filter(methodElement -> {
            boolean nonObjectMethod = !methodElement.getEnclosingElement().toString().equals("java.lang.Object");
            Set<Modifier> modifiers = methodElement.getModifiers();
            boolean publicMethod = modifiers.contains(Modifier.PUBLIC);
            boolean nonAbstractMethod = !modifiers.contains(Modifier.ABSTRACT);
            boolean nonStaticMethod = !modifiers.contains(Modifier.STATIC);

            return nonObjectMethod && publicMethod && nonAbstractMethod && nonStaticMethod;
        }).collect(Collectors.toList());
    }

    private JCMethodDecl buildMethodDecl(ExecutableElement methodElement, String annotationInstanceName)
            throws UnknownExpressionType {
        JCMethodDecl methodDecl = null;

        JCModifiers modifiers = treeMaker.Modifiers(Flags.PUBLIC);
        List<? extends VariableElement> elementParams = methodElement.getParameters();
        com.sun.tools.javac.util.List<JCVariableDecl> treeParams = buildTreeParamsFromElementParams(elementParams);

        String methodName = methodElement.getSimpleName().toString();
        TypeMirror returnTypeMirror = methodElement.getReturnType();

        JCExpression returnTypeExpression = buildJCExpressionFromType(returnTypeMirror);
        JCBlock methodBody = buildMethodBody(annotationInstanceName, methodName, returnTypeMirror, treeParams);

        methodDecl = treeMaker.MethodDef(modifiers, names.fromString(methodName), returnTypeExpression,
                com.sun.tools.javac.util.List.nil(), treeParams, com.sun.tools.javac.util.List.nil(), methodBody,
                null);

        return methodDecl;
    }

    private com.sun.tools.javac.util.List<JCVariableDecl> buildTreeParamsFromElementParams(
            List<? extends VariableElement> elementParams) throws UnknownExpressionType {
        List<JCVariableDecl> outputParams = new ArrayList<>();

        for (VariableElement paramElement : elementParams) {
            JCExpression paramType = buildJCExpressionFromType(paramElement.asType());
            JCVariableDecl paramDecl = treeMaker.VarDef(treeMaker.Modifiers(Flags.PARAMETER),
                    names.fromString(paramElement.toString()), paramType, null);
            outputParams.add(paramDecl);
        }

        return com.sun.tools.javac.util.List.from(outputParams);
    }

    private JCBlock buildMethodBody(String instanceName, String methodName, TypeMirror returnTypeMirror,
            List<JCVariableDecl> params) {
        // Identifies the instance variable in the Presenter e.g.
        // @Presenter(value=Foo.class, instanceName="foo")
        // class FooPresenter {
        // Foo foo; <-- "foo" is the instanceVarIdent
        // }
        JCIdent instanceVarIdent = treeMaker.Ident(names.fromString(instanceName));
        JCFieldAccess fieldAccess = treeMaker.Select(instanceVarIdent, names.fromString(methodName));
        com.sun.tools.javac.util.List<JCExpression> args = buildMethodArgsFromParams(params);
        JCMethodInvocation invocation = treeMaker.Apply(com.sun.tools.javac.util.List.<JCExpression>nil(), fieldAccess,
                args);

        JCStatement bodyStatement = buildMethodBodyReturnOrStatement(invocation, returnTypeMirror);
        com.sun.tools.javac.util.List<JCStatement> blockStatements = com.sun.tools.javac.util.List
                .from(new JCStatement[] { bodyStatement });
        return treeMaker.Block(0, blockStatements);
    }

    private JCStatement buildMethodBodyReturnOrStatement(JCMethodInvocation invocation, TypeMirror returnTypeMirror) {
        JCStatement methodBodyStatement = null;

        if (returnTypeMirror.getKind() == TypeKind.VOID) {
            methodBodyStatement = treeMaker.Exec(invocation);
        } else {
            methodBodyStatement = treeMaker.Return(invocation);
        }

        return methodBodyStatement;
    }

    private com.sun.tools.javac.util.List<JCExpression> buildMethodArgsFromParams(List<JCVariableDecl> params) {
        List<JCIdent> args = params.stream().map(
                variableDecl -> {
                    return treeMaker.Ident(variableDecl.getName());
                }).collect(Collectors.toList());

        return com.sun.tools.javac.util.List.from(args);
    }

    private JCExpression buildJCExpressionFromType(TypeMirror typeMirror) throws UnknownExpressionType {
        JCExpression builtExpression = null;

        TypeKind typeKind = typeMirror.getKind();
        if (typeKind == TypeKind.VOID) {
            builtExpression = buildVoidExpression();
        } else if (typeKind == TypeKind.DECLARED) {
            builtExpression = buildDeclaredExpressionFromType(typeMirror);
        } else if (typeKind == TypeKind.ARRAY) {
            builtExpression = buildArrayExpressionFromType(typeMirror);
        } else if (typeKind.isPrimitive()) {
            builtExpression = buildPrimitiveExpressionFromType(typeMirror);
        } else {
            throw new UnknownExpressionType("unknown return TypeKind " + typeKind);
        }

        return builtExpression;
    }

    private JCExpression buildVoidExpression() {
        return treeMaker.TypeIdent(TypeTag.VOID);
    }

    private JCExpression buildDeclaredExpressionFromType(TypeMirror typeMirror) {
        Type.ClassType classType = (Type.ClassType) typeMirror;
        return treeMaker.Ident(classType.tsym);
    }

    private JCExpression buildArrayExpressionFromType(TypeMirror typeMirror) throws UnknownExpressionType {
        ArrayType arrayType = (ArrayType) typeMirror;
        JCExpression elemType = buildJCExpressionFromType(arrayType.elemtype);

        return treeMaker.TypeArray(elemType);
    }

    private JCExpression buildPrimitiveExpressionFromType(TypeMirror typeMirror) {
        JCPrimitiveType primitiveReturnType = (JCPrimitiveType) types.getPrimitiveType(typeMirror.getKind());
        return treeMaker.TypeIdent(primitiveReturnType.getTag());
    }

    private Optional<TypeElement> getProcessorTargetTypeElement(TypeMirror typeMirror) {
        Element element = this.processingEnv.getTypeUtils().asElement(typeMirror);
        if (element instanceof TypeElement) {
            return Optional.of((TypeElement) element);
        }

        return Optional.empty();
    }

}
