package com.beggar.hotfix.autopatch;

import java.util.List;

import org.gradle.api.logging.Logger;

import com.android.annotations.NonNull;
import com.beggar.hotfix.base.Constants;
import com.beggar.hotfix.base.annotation.Add;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * author: BeggarLan
 * created on: 2022/6/17 7:11 下午
 * description: 注解处理
 */
public class HotFixAnnotationHandler {

  private static final String TAG = "HotFixAnnotationHandler";

  @NonNull
  private final AutoPatchConfig mAutoPatchConfig;
  @NonNull
  private final List<CtClass> mCtClasses;
  @NonNull
  private final Logger mLogger;

  public HotFixAnnotationHandler(
      @NonNull AutoPatchConfig autoPatchConfig,
      @NonNull List<CtClass> ctClasses,
      @NonNull Logger logger) {
    mAutoPatchConfig = autoPatchConfig;
    mCtClasses = ctClasses;
    mLogger = logger;
  }

  /**
   * 处理 Add和Modify
   */
  public void handleAnnotation() {
    mLogger.quiet(TAG + " handleAnnotation start.");

    // 拿到两个注解class
    Class addAnnotationClass;
    Class modifyAnnotationClass;
    try {
      addAnnotationClass =
          mCtClasses.get(0).getClassPool().get(Constants.ADD_CLASS_NAME).toClass();
      modifyAnnotationClass =
          mCtClasses.get(0).getClassPool().get(Constants.MODIFY_CLASS_NAME).toClass();
    } catch (CannotCompileException | NotFoundException e) {
      mLogger.error(TAG + " handleAnnotation: get Add or Modify class error");
      e.printStackTrace();
      return;
    }

    for (CtClass ctClass : mCtClasses) {
      // 先处理该类被add
      boolean isClassAdd = handleAddNewClass(ctClass, addAnnotationClass);
      if (isClassAdd) {
        // 新增类直接跳过下方
        continue;
      }

      // 处理新增方法
      handleAddNewMethod(ctClass, addAnnotationClass);
      // 处理修改的方法
      handleModifyMethod(ctClass, modifyAnnotationClass);
    }
    mLogger.quiet(TAG + " handleAnnotation end.");
  }

  /**
   * 新增类处理
   *
   * @param addAnnotationClass add注解的class
   * @return {@code true} 该类是新增类(被add注解)
   */
  private boolean handleAddNewClass(@NonNull CtClass ctClass, @NonNull Class addAnnotationClass) {
    try {
      Add addAnnotation = (Add) ctClass.getAnnotation(addAnnotationClass);
      if (addAnnotation != null) {
        String className = ctClass.getName();
        mLogger.quiet(TAG + " handleAddNewClass: " + className);
        if (!mAutoPatchConfig.mNewClassList.contains(className)) {
          mAutoPatchConfig.mNewClassList.add(className);
        }
        return true;
      }
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    return false;
  }

  /**
   * 新增方法
   *
   * @param addAnnotationClass add注解的class
   */
  private void handleAddNewMethod(@NonNull CtClass ctClass, @NonNull Class addAnnotationClass) {
//    ctClass.defrost();
    CtMethod[] declaredMethods = ctClass.getDeclaredMethods();
    for (CtMethod ctMethod : declaredMethods) {
      try {
        if (ctMethod.getAnnotation(addAnnotationClass) != null) {
          // such as javassist.CtMethod.setBody(String).
          String longName = JavassistUtil.getMethodSignatureName(ctMethod);
          if (!mAutoPatchConfig.mNewMethodList.contains(longName)) {
            mAutoPatchConfig.mNewMethodList.add(longName);
          }
        }
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * 处理修改的方法
   *
   * @param modifyAnnotationClass modify注解的class
   */
  // TODO: 2022/6/21 类修改和lambda表达式修改
  private void handleModifyMethod(@NonNull CtClass ctClass, @NonNull Class modifyAnnotationClass) {
    CtMethod[] declaredMethods = ctClass.getDeclaredMethods();
    for (CtMethod ctMethod : declaredMethods) {
      try {
        if (ctMethod.getAnnotation(modifyAnnotationClass) == null) {
          continue;
        }
        String methodLongName = JavassistUtil.getMethodSignatureName(ctMethod);
        if (!mAutoPatchConfig.mCodeInsertMethodMap.containsKey(methodLongName)) {
          throw new IllegalArgumentException(
              TAG + " handleModifyMethod： method has not insert code, " + methodLongName);
        }

        // such as javassist.CtMethod.setBody(String).
        if (!mAutoPatchConfig.mModifyMethodSignatureList.contains(methodLongName)) {
          mAutoPatchConfig.mModifyMethodSignatureList.add(methodLongName);
        }
        String className = ctMethod.getDeclaringClass().getName();
        if (!mAutoPatchConfig.mModifyClassList.contains(className)) {
          mAutoPatchConfig.mModifyClassList.add(className);
        }
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
    }
  }

}
