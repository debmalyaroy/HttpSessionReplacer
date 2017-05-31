package com.amadeus.session.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class CommonHelpers implements Opcodes {
    static void addCallMethod(String className, ClassVisitor cw) {
        MethodVisitor mv;
        {
            mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC + ACC_VARARGS, "$$call",
                    "(Ljavax/servlet/ServletContext;I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            mv.visitCode();
            Label l0 = new Label();
            Label l1 = new Label();
            Label l2 = new Label();
            mv.visitTryCatchBlock(l0, l1, l2, "java/lang/InstantiationException");
            Label l3 = new Label();
            mv.visitTryCatchBlock(l0, l1, l3, "java/lang/IllegalAccessException");
            Label l4 = new Label();
            mv.visitTryCatchBlock(l0, l1, l4, "java/lang/ClassNotFoundException");
            Label l5 = new Label();
            mv.visitTryCatchBlock(l0, l1, l5, "java/lang/NoSuchMethodException");
            Label l6 = new Label();
            mv.visitTryCatchBlock(l0, l1, l6, "java/lang/Error");
            Label l7 = new Label();
            mv.visitTryCatchBlock(l0, l1, l7, "java/lang/RuntimeException");
            Label l8 = new Label();
            mv.visitTryCatchBlock(l0, l1, l8, "java/lang/Throwable");
            Label l9 = new Label();
            Label l10 = new Label();
            Label l11 = new Label();
            mv.visitTryCatchBlock(l9, l10, l11, "java/lang/Error");
            Label l12 = new Label();
            mv.visitTryCatchBlock(l9, l10, l12, "java/lang/RuntimeException");
            Label l13 = new Label();
            mv.visitTryCatchBlock(l9, l10, l13, "java/lang/Throwable");
            Label l14 = new Label();
            mv.visitLabel(l14);
            mv.visitLineNumber(136, l14);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitLdcInsn("com.amadeus.session.servlet.SessionHelpers.methods");
            mv.visitMethodInsn(INVOKEINTERFACE, "javax/servlet/ServletContext", "getAttribute",
                    "(Ljava/lang/String;)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/invoke/MethodHandle;");
            mv.visitVarInsn(ASTORE, 3);
            Label l15 = new Label();
            mv.visitLabel(l15);
            mv.visitLineNumber(137, l15);
            mv.visitVarInsn(ALOAD, 3);
            Label l16 = new Label();
            mv.visitJumpInsn(IFNONNULL, l16);
            Label l17 = new Label();
            mv.visitLabel(l17);
            mv.visitLineNumber(138, l17);
            mv.visitInsn(ACONST_NULL);
            mv.visitVarInsn(ASTORE, 4);
            Label l18 = new Label();
            mv.visitLabel(l18);
            mv.visitLineNumber(139, l18);
            mv.visitFieldInsn(GETSTATIC, className, "$$isServlet3", "Z");
            Label l19 = new Label();
            mv.visitJumpInsn(IFEQ, l19);
            Label l20 = new Label();
            mv.visitLabel(l20);
            mv.visitLineNumber(140, l20);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEINTERFACE, "javax/servlet/ServletContext", "getClassLoader",
                    "()Ljava/lang/ClassLoader;", true);
            mv.visitVarInsn(ASTORE, 4);
            mv.visitLabel(l19);
            mv.visitLineNumber(142, l19);
            mv.visitFrame(Opcodes.F_APPEND, 2,
                    new Object[] { "[Ljava/lang/invoke/MethodHandle;", "java/lang/ClassLoader" }, 0, null);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitJumpInsn(IFNONNULL, l0);
            Label l21 = new Label();
            mv.visitLabel(l21);
            mv.visitLineNumber(143, l21);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "getContextClassLoader", "()Ljava/lang/ClassLoader;",
                    false);
            mv.visitVarInsn(ASTORE, 4);
            mv.visitLabel(l0);
            mv.visitLineNumber(146, l0);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitLdcInsn("com.amadeus.session.servlet.SessionHelpers");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ClassLoader", "loadClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;", false);
            mv.visitVarInsn(ASTORE, 5);
            Label l22 = new Label();
            mv.visitLabel(l22);
            mv.visitLineNumber(147, l22);
            mv.visitVarInsn(ALOAD, 5);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "newInstance", "()Ljava/lang/Object;", false);
            mv.visitVarInsn(ASTORE, 6);
            Label l23 = new Label();
            mv.visitLabel(l23);
            mv.visitLineNumber(148, l23);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup",
                    "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
            mv.visitVarInsn(ASTORE, 7);
            Label l24 = new Label();
            mv.visitLabel(l24);
            mv.visitLineNumber(149, l24);
            mv.visitLdcInsn(Type.getType("[Ljava/lang/invoke/MethodHandle;"));
            mv.visitLdcInsn(Type.getType("Ljavax/servlet/ServletContext;"));
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType",
                    "(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
            mv.visitVarInsn(ASTORE, 8);
            Label l25 = new Label();
            mv.visitLabel(l25);
            mv.visitLineNumber(150, l25);
            mv.visitVarInsn(ALOAD, 7);
            mv.visitVarInsn(ALOAD, 6);
            mv.visitLdcInsn("initSessionManagement");
            mv.visitVarInsn(ALOAD, 8);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "bind",
                    "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                    false);
            mv.visitVarInsn(ASTORE, 9);
            Label l26 = new Label();
            mv.visitLabel(l26);
            mv.visitLineNumber(151, l26);
            mv.visitVarInsn(ALOAD, 9);
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(AASTORE);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeWithArguments",
                    "([Ljava/lang/Object;)Ljava/lang/Object;", false);
            mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/invoke/MethodHandle;");
            mv.visitVarInsn(ASTORE, 3);
            mv.visitLabel(l1);
            mv.visitLineNumber(152, l1);
            mv.visitJumpInsn(GOTO, l16);
            mv.visitLabel(l2);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/InstantiationException" });
            mv.visitVarInsn(ASTORE, 5);
            Label l27 = new Label();
            mv.visitLabel(l27);
            mv.visitLineNumber(153, l27);
            mv.visitVarInsn(ALOAD, 5);
            mv.visitMethodInsn(INVOKESTATIC, className, "$$invalidState",
                    "(Ljava/lang/Throwable;)Ljava/lang/IllegalStateException;", false);
            mv.visitInsn(ATHROW);
            mv.visitLabel(l3);
            mv.visitLineNumber(154, l3);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/IllegalAccessException" });
            mv.visitVarInsn(ASTORE, 5);
            Label l28 = new Label();
            mv.visitLabel(l28);
            mv.visitLineNumber(155, l28);
            mv.visitVarInsn(ALOAD, 5);
            mv.visitMethodInsn(INVOKESTATIC, className, "$$invalidState",
                    "(Ljava/lang/Throwable;)Ljava/lang/IllegalStateException;", false);
            mv.visitInsn(ATHROW);
            mv.visitLabel(l4);
            mv.visitLineNumber(156, l4);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/ClassNotFoundException" });
            mv.visitVarInsn(ASTORE, 5);
            Label l29 = new Label();
            mv.visitLabel(l29);
            mv.visitLineNumber(157, l29);
            mv.visitVarInsn(ALOAD, 5);
            mv.visitMethodInsn(INVOKESTATIC, className, "$$invalidState",
                    "(Ljava/lang/Throwable;)Ljava/lang/IllegalStateException;", false);
            mv.visitInsn(ATHROW);
            mv.visitLabel(l5);
            mv.visitLineNumber(158, l5);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/NoSuchMethodException" });
            mv.visitVarInsn(ASTORE, 5);
            Label l30 = new Label();
            mv.visitLabel(l30);
            mv.visitLineNumber(159, l30);
            mv.visitVarInsn(ALOAD, 5);
            mv.visitMethodInsn(INVOKESTATIC, className, "$$invalidState",
                    "(Ljava/lang/Throwable;)Ljava/lang/IllegalStateException;", false);
            mv.visitInsn(ATHROW);
            mv.visitLabel(l6);
            mv.visitLineNumber(160, l6);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/Error" });
            mv.visitVarInsn(ASTORE, 5);
            Label l31 = new Label();
            mv.visitLabel(l31);
            mv.visitLineNumber(161, l31);
            mv.visitVarInsn(ALOAD, 5);
            mv.visitInsn(ATHROW);
            mv.visitLabel(l7);
            mv.visitLineNumber(162, l7);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/RuntimeException" });
            mv.visitVarInsn(ASTORE, 5);
            Label l32 = new Label();
            mv.visitLabel(l32);
            mv.visitLineNumber(163, l32);
            mv.visitVarInsn(ALOAD, 5);
            mv.visitInsn(ATHROW);
            mv.visitLabel(l8);
            mv.visitLineNumber(164, l8);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });
            mv.visitVarInsn(ASTORE, 5);
            Label l33 = new Label();
            mv.visitLabel(l33);
            mv.visitLineNumber(165, l33);
            mv.visitVarInsn(ALOAD, 5);
            mv.visitMethodInsn(INVOKESTATIC, className, "$$invalidState",
                    "(Ljava/lang/Throwable;)Ljava/lang/IllegalStateException;", false);
            mv.visitInsn(ATHROW);
            mv.visitLabel(l16);
            mv.visitLineNumber(168, l16);
            mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitJumpInsn(IFGE, l9);
            Label l34 = new Label();
            mv.visitLabel(l34);
            mv.visitLineNumber(169, l34);
            mv.visitInsn(ACONST_NULL);
            mv.visitInsn(ARETURN);
            mv.visitLabel(l9);
            mv.visitLineNumber(172, l9);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitInsn(AALOAD);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeWithArguments",
                    "([Ljava/lang/Object;)Ljava/lang/Object;", false);
            mv.visitLabel(l10);
            mv.visitInsn(ARETURN);
            mv.visitLabel(l11);
            mv.visitLineNumber(173, l11);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/Error" });
            mv.visitVarInsn(ASTORE, 4);
            Label l35 = new Label();
            mv.visitLabel(l35);
            mv.visitLineNumber(174, l35);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitInsn(ATHROW);
            mv.visitLabel(l12);
            mv.visitLineNumber(175, l12);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/RuntimeException" });
            mv.visitVarInsn(ASTORE, 4);
            Label l36 = new Label();
            mv.visitLabel(l36);
            mv.visitLineNumber(176, l36);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitInsn(ATHROW);
            mv.visitLabel(l13);
            mv.visitLineNumber(177, l13);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });
            mv.visitVarInsn(ASTORE, 4);
            Label l37 = new Label();
            mv.visitLabel(l37);
            mv.visitLineNumber(178, l37);
            mv.visitVarInsn(ALOAD, 4);
            mv.visitMethodInsn(INVOKESTATIC, className, "$$invalidState",
                    "(Ljava/lang/Throwable;)Ljava/lang/IllegalStateException;", false);
            mv.visitInsn(ATHROW);
            Label l38 = new Label();
            mv.visitLabel(l38);
            mv.visitLocalVariable("servletContext", "Ljavax/servlet/ServletContext;", null, l14, l38, 0);
            mv.visitLocalVariable("key", "I", null, l14, l38, 1);
            mv.visitLocalVariable("args", "[Ljava/lang/Object;", null, l14, l38, 2);
            mv.visitLocalVariable("methods", "[Ljava/lang/invoke/MethodHandle;", null, l15, l38, 3);
            mv.visitLocalVariable("classLoader", "Ljava/lang/ClassLoader;", null, l18, l16, 4);
            mv.visitLocalVariable("clazz", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", l22, l1, 5);
            mv.visitLocalVariable("instance", "Ljava/lang/Object;", null, l23, l1, 6);
            mv.visitLocalVariable("lookup", "Ljava/lang/invoke/MethodHandles$Lookup;", null, l24, l1, 7);
            mv.visitLocalVariable("mt", "Ljava/lang/invoke/MethodType;", null, l25, l1, 8);
            mv.visitLocalVariable("initSessionManagement", "Ljava/lang/invoke/MethodHandle;", null, l26, l1, 9);
            mv.visitLocalVariable("e", "Ljava/lang/InstantiationException;", null, l27, l3, 5);
            mv.visitLocalVariable("e", "Ljava/lang/IllegalAccessException;", null, l28, l4, 5);
            mv.visitLocalVariable("e", "Ljava/lang/ClassNotFoundException;", null, l29, l5, 5);
            mv.visitLocalVariable("e", "Ljava/lang/NoSuchMethodException;", null, l30, l6, 5);
            mv.visitLocalVariable("e", "Ljava/lang/Error;", null, l31, l7, 5);
            mv.visitLocalVariable("e", "Ljava/lang/RuntimeException;", null, l32, l8, 5);
            mv.visitLocalVariable("e", "Ljava/lang/Throwable;", null, l33, l16, 5);
            mv.visitLocalVariable("e", "Ljava/lang/Error;", null, l35, l12, 4);
            mv.visitLocalVariable("e", "Ljava/lang/RuntimeException;", null, l36, l13, 4);
            mv.visitLocalVariable("e", "Ljava/lang/Throwable;", null, l37, l38, 4);
            mv.visitMaxs(5, 10);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, "$$invalidState",
                    "(Ljava/lang/Throwable;)Ljava/lang/IllegalStateException;", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(421, l0);
            mv.visitTypeInsn(NEW, "java/lang/IllegalStateException");
            mv.visitInsn(DUP);
            mv.visitLdcInsn("Unable to load or instrument com.amadeus.session.servlet.SessionHelpers");
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>",
                    "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
            mv.visitInsn(ARETURN);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLocalVariable("e", "Ljava/lang/Throwable;", null, l0, l1, 0);
            mv.visitMaxs(4, 1);
            mv.visitEnd();
        }
    }

    static void addLogError(ClassVisitor cw) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC + ACC_VARARGS, "$$error",
                "(Ljava/lang/String;[Ljava/lang/Object;)V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitLineNumber(433, l0);
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("SessionAgent: [ERROR] %s");
        mv.visitInsn(ICONST_1);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        mv.visitInsn(DUP);
        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "format",
                "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);
        mv.visitInsn(AASTORE);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "format",
                "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLineNumber(434, l1);
        mv.visitVarInsn(ALOAD, 1);
        Label l2 = new Label();
        mv.visitJumpInsn(IFNULL, l2);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitInsn(ICONST_1);
        mv.visitJumpInsn(IF_ICMPLE, l2);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitInsn(ICONST_1);
        mv.visitInsn(ISUB);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(INSTANCEOF, "java/lang/Throwable");
        mv.visitJumpInsn(IFEQ, l2);
        Label l3 = new Label();
        mv.visitLabel(l3);
        mv.visitLineNumber(435, l3);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ARRAYLENGTH);
        mv.visitInsn(ICONST_1);
        mv.visitInsn(ISUB);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, "java/lang/Throwable");
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "printStackTrace", "(Ljava/io/PrintStream;)V", false);
        mv.visitLabel(l2);
        mv.visitLineNumber(437, l2);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitInsn(RETURN);
        Label l4 = new Label();
        mv.visitLabel(l4);
        mv.visitLocalVariable("format", "Ljava/lang/String;", null, l0, l4, 0);
        mv.visitLocalVariable("args", "[Ljava/lang/Object;", null, l0, l4, 1);
        mv.visitMaxs(7, 2);
        mv.visitEnd();
    }

    static void addIsServlet3(ClassVisitor cw) {
        FieldVisitor fv = cw.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC, "$$isServlet3", "Z", null, null);
        fv.visitEnd();

        MethodVisitor mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, "$$isServlet3", "()Z", null, null);
        mv.visitCode();
        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();
        mv.visitTryCatchBlock(l0, l1, l2, "java/lang/NoSuchMethodException");
        mv.visitLabel(l0);
        mv.visitLineNumber(446, l0);
        mv.visitLdcInsn(Type.getType("Ljavax/servlet/ServletRequest;"));
        mv.visitLdcInsn("startAsync");
        mv.visitInsn(ICONST_0);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
        mv.visitInsn(POP);
        mv.visitLabel(l1);
        mv.visitLineNumber(447, l1);
        mv.visitInsn(ICONST_1);
        mv.visitInsn(IRETURN);
        mv.visitLabel(l2);
        mv.visitLineNumber(448, l2);
        mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/NoSuchMethodException" });
        mv.visitVarInsn(ASTORE, 0);
        Label l3 = new Label();
        mv.visitLabel(l3);
        mv.visitLineNumber(449, l3);
        mv.visitInsn(ICONST_0);
        mv.visitInsn(IRETURN);
        Label l4 = new Label();
        mv.visitLabel(l4);
        mv.visitLocalVariable("e", "Ljava/lang/NoSuchMethodException;", null, l3, l4, 0);
        mv.visitMaxs(3, 1);
        mv.visitEnd();
    }
}
