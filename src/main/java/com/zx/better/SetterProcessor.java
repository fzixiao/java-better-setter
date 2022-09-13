package com.zx.better;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Setter方法
 *
 * @author : ZX
 * @since : 2022/09/11 18:01
 */
@SupportedAnnotationTypes("com.zx.better.SetterProcessor")
// @SupportedAnnotationTypes("*")
// 支持的Java版本号
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SetterProcessor extends AbstractProcessor {
    // 编译时期输出日志
    private Messager message;
    private JavacTrees javacTrees;
    // 用于创建语法树节点
    private TreeMaker treeMaker;
    private Names names;

    /**
     * 给成员变量初始化赋值
     *
     * @param processingEnv
     * @return void
     * @author ZX
     * @since 2022/09/11 18:58
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        // ProcessingEnvironment:注释处理工具框架
        super.init(processingEnv);
        // 给成员变量初始化赋值
        this.message = processingEnv.getMessager();
        this.javacTrees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    /**
     * 重写注解处理方法
     *
     * @param annotations
     * @param roundEnv
     * @return boolean
     * @author ZX
     * @since 2022/09/11 18:59
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println("setter方法");
        // 获取被@SetterPro标记的元素
        Set<? extends Element> elementSet = roundEnv.getElementsAnnotatedWith(SetterPro.class);
        // 遍历，并对包含注解的元素进行处理
        elementSet.forEach(element -> {
            // 将element转化为JCTree
            JCTree elementJcTree = javacTrees.getTree(element);
            elementJcTree.accept(new TreeTranslator() {
                // 获取类信息，并进行修改
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                    // 创建变量语法树的节点的List
                    java.util.List<JCTree.JCVariableDecl> jcVariableDeclList = new ArrayList<>();
                    // 遍历类的信息
                    for (JCTree tree : jcClassDecl.defs) {
                        // 判断语法树节点是否是变量类型，如果是就把他存在List里
                        if (tree.getKind().equals(Tree.Kind.VARIABLE)) {
                            // 将节点类型强转并追加进变量List
                            jcVariableDeclList.add((JCTree.JCVariableDecl) tree);
                        }
                    }
                    // 获取类的类型
                    Type ClassType = jcClassDecl.type;
                    // 遍历list,生成setter方法
                    jcVariableDeclList.forEach(jcVariableDecl -> {
                        // 输出日志
                        message.printMessage(Diagnostic.Kind.NOTE, " get " + jcVariableDecl.getName() + " 已经被处理了");
                        treeMaker.pos=jcVariableDecl.pos;
                        handleElement(jcClassDecl, jcVariableDecl, ClassType);
                    });
                    super.visitClassDef(jcClassDecl);
                }
            });
        });
        return true;
    }

    private void handleElement(JCTree.JCClassDecl jcClassDecl, JCTree.JCVariableDecl jcVariableDecl, Type classType) {

        JCTree.JCMethodDecl newSetterProMethod = newSetterProMethod(jcVariableDecl, classType);
        // 往类节点追加方法
        jcClassDecl.defs.append(newSetterProMethod);

    }

    private JCTree.JCMethodDecl newSetterProMethod(JCTree.JCVariableDecl jcVariableDecl, Type classType) {
        // 返回值类型
        JCTree.JCExpression type = treeMaker.Type(classType);
        // 变量名
        String filedName = jcVariableDecl.getName().toString();
        // setter方法名
        Name name = names.fromString("set" + filedName.substring(0, 1).toUpperCase() + filedName.substring(1));
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        // 节点添加赋值语句
        statements.append(
                treeMaker.Exec(
                        treeMaker.Assign(
                                treeMaker.Select(
                                        treeMaker.Ident(names.fromString("this")),
                                        names.fromString(jcVariableDecl.name.toString())
                                ),
                                treeMaker.Ident(names.fromString(jcVariableDecl.name.toString()))
                        )
                )
        );
        // 节点添加return语句
        statements.append(treeMaker.Return(type));
        JCTree.JCBlock body = treeMaker.Block(0, statements.toList());

        // 生成入参
        JCTree.JCVariableDecl param = treeMaker.VarDef(treeMaker.Modifiers(Flags.PARAMETER), jcVariableDecl.getName(), jcVariableDecl.vartype, null);

        List<JCTree.JCVariableDecl> paramList = List.of(param);

        return treeMaker.MethodDef(
                treeMaker.Modifiers(Flags.PUBLIC), // 方法限定值
                name, // 方法名
                type, // 返回类型
                List.nil(),
                paramList, // 入参
                List.nil(),
                body,
                null
        );

    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return super.getSupportedSourceVersion();
    }


}
