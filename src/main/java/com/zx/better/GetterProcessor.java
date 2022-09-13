package com.zx.better;

/**
 * @author : ZX
 * @since : 2022/09/12 14:53
 */

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
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
import java.util.Set;

// 对Getter感兴趣
@SupportedAnnotationTypes("com.zx.better.Getter")
// 支持的版本，使用1.8就写这个
//@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class GetterProcessor extends AbstractProcessor {

    // 编译时期输入日志的
    private Messager messager;

    // 将Element转换为JCTree的工具,提供了待处理的抽象语法树
    private JavacTrees trees;

    // 封装了创建AST节点的一些方法
    private TreeMaker treeMaker;

    // 提供了创建标识符的方法
    private Names names;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        // 获取被@Getter注解标记的所有元素(这个元素可能是类、变量、方法等等)
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(Getter.class);

        set.forEach(element -> {
            // 将Element转换为JCTree
            JCTree jcTree = trees.getTree(element);
            jcTree.accept(new TreeTranslator() {
                /***
                 * JCTree.Visitor有很多方法，我们可以通过重写对应的方法,(从该方法的形参中)来获取到我们想要的信息:
                 * 如: 重写visitClassDef方法， 获取到类的信息;
                 *     重写visitMethodDef方法， 获取到方法的信息;
                 *     重写visitVarDef方法， 获取到变量的信息;
                 * @param jcClassDecl
                 */
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {

                    // 创建一个变量语法树节点的List
                    List<JCTree.JCVariableDecl> jcVariableDeclList = List.nil();

                    // 遍历defs，即是类定义的详细语句，包括字段、方法的定义等等
                    for (JCTree tree : jcClassDecl.defs) {
                        if (tree.getKind().equals(Tree.Kind.VARIABLE)) {
                            JCTree.JCVariableDecl jcVariableDecl = (JCTree.JCVariableDecl) tree;
                            jcVariableDeclList = jcVariableDeclList.append(jcVariableDecl);
                        }
                    }
                    // 对于变量进行生成方法的操作
                    jcVariableDeclList.forEach(jcVariableDecl -> {
                        messager.printMessage(Diagnostic.Kind.NOTE, "get " + jcVariableDecl.getName() + " has been processed");
                        treeMaker.pos = jcVariableDecl.pos;
                        // 类里的前面追加生成的Getter方法
                        jcClassDecl.defs = jcClassDecl.defs.prepend(makeGetterMethodDecl(jcVariableDecl));
                    });
                    super.visitClassDef(jcClassDecl);
                }

            });
        });
        // 我们有修改过AST，所以返回true
        return true;
    }

    private JCTree.JCMethodDecl makeGetterMethodDecl(JCTree.JCVariableDecl jcVariableDecl) {
        /***
         * JCStatement：声明语法树节点，常见的子类如下
         * JCBlock：语句块语法树节点
         * JCReturn：return语句语法树节点
         * JCClassDecl：类定义语法树节点
         * JCVariableDecl：字段/变量定义语法树节点
         * JCMethodDecl：方法定义语法树节点
         * JCModifiers：访问标志语法树节点
         * JCExpression：表达式语法树节点，常见的子类如下
         * JCAssign：赋值语句语法树节点
         * JCIdent：标识符语法树节点，可以是变量，类型，关键字等等
         */
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        statements.append(treeMaker.Return(treeMaker.Select(treeMaker.Ident(names.fromString("this")), jcVariableDecl.getName())));

        JCTree.JCBlock body = treeMaker.Block(0, statements.toList());

        return treeMaker.MethodDef(
                treeMaker.Modifiers(Flags.PUBLIC),// mods：访问标志
                getNewMethodName(jcVariableDecl.getName()),// name：方法名
                jcVariableDecl.vartype,// restype：返回类型
                List.nil(),// typarams：泛型参数列表
                List.nil(),// params：参数列表
                List.nil(),// thrown：异常声明列表
                body,// 方法体
                null);
    }

    private Name getNewMethodName(Name name) {
        String s = name.toString();
        return names.fromString("get" + s.substring(0, 1).toUpperCase() + s.substring(1, name.length()));
    }
}

