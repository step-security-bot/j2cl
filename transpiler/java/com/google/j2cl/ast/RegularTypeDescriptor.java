/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.ast;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.j2cl.ast.processors.Visitable;

import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A (by name) reference to a class.
 */
@Visitable
public class RegularTypeDescriptor extends TypeDescriptor {
  private ITypeBinding typeBinding;

  protected ImmutableList<String> packageComponents;
  protected ImmutableList<String> classComponents;
  protected boolean isRaw;
  protected ImmutableList<TypeDescriptor> typeArgumentDescriptors;

  // JsInterop properties
  protected boolean isJsFunction;
  protected boolean isJsType;
  protected boolean isNative;
  protected String jsTypeNamespace;
  protected String jsTypeName;

  RegularTypeDescriptor(ITypeBinding typeBinding) {
    this.typeBinding = typeBinding;
    if (typeBinding != null) {
      setJsInteropProperties();
    }
  }

  private void setJsInteropProperties() {
    IAnnotationBinding jsTypeAnnotation = JsInteropUtils.getJsTypeAnnotation(typeBinding);
    if (jsTypeAnnotation != null) {
      isJsType = true;
      isNative = JsInteropUtils.isNative(jsTypeAnnotation);
      jsTypeNamespace = JsInteropUtils.getJsNamespace(jsTypeAnnotation);
      jsTypeName = JsInteropUtils.getJsName(jsTypeAnnotation);
      return;
    }
    isJsFunction = JsInteropUtils.isJsFunction(typeBinding);
  }

  public ImmutableList<String> getPackageComponents() {
    // Lazily initialize packageComponents.
    if (packageComponents == null) {
      Preconditions.checkNotNull(typeBinding);
      packageComponents = ImmutableList.copyOf(TypeProxyUtils.getPackageComponents(typeBinding));
    }
    return packageComponents;
  }

  public ImmutableList<String> getClassComponents() {
    // Lazily initialize classComponents.
    if (classComponents == null) {
      Preconditions.checkNotNull(typeBinding);
      classComponents = ImmutableList.copyOf(TypeProxyUtils.getClassComponents(typeBinding));
    }
    return classComponents;
  }

  @Override
  public boolean isRaw() {
    return false;
  }

  @Override
  public ImmutableList<TypeDescriptor> getTypeArgumentDescriptors() {
    // Lazily initialize typeArgumentDescriptors.
    if (typeArgumentDescriptors == null) {
      Preconditions.checkNotNull(typeBinding);
      typeArgumentDescriptors =
          ImmutableList.copyOf(TypeProxyUtils.getTypeArgumentDescriptors(typeBinding));
    }
    return typeArgumentDescriptors;
  }

  @Override
  public boolean isPrimitive() {
    return typeBinding != null && typeBinding.isPrimitive();
  }

  @Override
  public boolean isTypeVariable() {
    return typeBinding != null && typeBinding.isTypeVariable();
  }

  @Override
  public boolean isWildCard() {
    return (typeBinding != null && (typeBinding.isWildcardType() || typeBinding.isCapture()));
  }

  @Override
  public String getBinaryName() {
    return Joiner.on(".")
        .join(
            Iterables.concat(
                getPackageComponents(),
                Collections.singleton(Joiner.on("$").join(getClassComponents()))));
  }

  @Override
  public String getClassName() {
    if (isPrimitive()) {
      return "$" + getSimpleName();
    }
    if (getSimpleName().equals("?")) {
      return "?";
    }
    if (isTypeVariable()) {
      Preconditions.checkArgument(
          getClassComponents().size() > 1,
          "Type Variable (not including wild card type) should have at least two name components");
      // skip the top level class component for better output readability.
      List<String> nameComponents =
          new ArrayList<>(getClassComponents().subList(1, getClassComponents().size()));

      // move the prefix in the simple name to the class name to avoid collisions between method-
      // level and class-level type variable and avoid variable name starts with a number.
      // concat class components to avoid collisions between type variables in inner/outer class.
      // use '_' instead of '$' because '$' is not allowed in template variable name in closure.
      String simpleName = getSimpleName();
      nameComponents.set(
          nameComponents.size() - 1, simpleName.substring(simpleName.indexOf('_') + 1));
      String prefix = simpleName.substring(0, simpleName.indexOf('_') + 1);

      return prefix + Joiner.on('_').join(nameComponents);
    }
    return Joiner.on('$').join(getClassComponents());
  }

  @Override
  public String getSimpleName() {
    return Iterables.getLast(getClassComponents());
  }

  @Override
  public String getSourceName() {
    // For type variable, we use its binary name from JDT directly, which will ensure the uniqueness
    // of each type variable. getBinaryName() computed by J2CL does not ensure the uniqueness in the
    // case where two type variables with the same name are declared in two different methods that
    // overload each other. Because J2cl only put the method name, not the full signature in the
    // class components.
    if (isTypeVariable()) {
      return typeBinding.getBinaryName();
    }
    return getBinaryName() + getTypeArgumentsName();
  }

  private String getTypeArgumentsName() {
    if (isParameterizedType()) {
      return String.format(
          "<%s>",
          Joiner.on(", ")
              .join(
                  Lists.transform(
                      getTypeArgumentDescriptors(),
                      new Function<TypeDescriptor, String>() {
                        @Override
                        public String apply(TypeDescriptor typeDescriptor) {
                          return typeDescriptor.getSourceName();
                        }
                      })));
    }
    return "";
  }

  @Override
  public String getPackageName() {
    return Joiner.on(".").join(getPackageComponents());
  }

  @Override
  public boolean isArray() {
    return false;
  }

  @Override
  public int getDimensions() {
    return 0;
  }

  @Override
  public TypeDescriptor getLeafTypeDescriptor() {
    return null;
  }

  @Override
  public boolean isParameterizedType() {
    return !getTypeArgumentDescriptors().isEmpty();
  }

  /**
   * Raw type of a parameterized type is the type without type parameters or type arguments.
   * Raw type of a type variable is its bound.
   * Otherwise, raw type is the type itself.
   */
  @Override
  public TypeDescriptor getRawTypeDescriptor() {
    if (isParameterizedType()) {
      return TypeDescriptor.createSynthetic(
          getPackageComponents(),
          getClassComponents(),
          isJsFunction,
          isJsType,
          isNative,
          jsTypeNamespace,
          jsTypeName);
    }
    if (isTypeVariable()) {
      Preconditions.checkNotNull(typeBinding);
      return TypeProxyUtils.createTypeDescriptor(typeBinding.getErasure());
    }
    return this;
  }

  @Override
  public TypeDescriptor getSuperTypeDescriptor() {
    // Will return a consistent interned copy, should be decently fast.
    return TypeProxyUtils.createTypeDescriptor(typeBinding.getSuperclass());
  }

  @Override
  public TypeDescriptor getEnclosingTypeDescriptor() {
    // Will return a consistent interned copy, should be decently fast.
    return TypeProxyUtils.createTypeDescriptor(typeBinding.getDeclaringClass());
  }

  @Override
  public Visibility getVisibility() {
    return TypeProxyUtils.getVisibility(typeBinding.getModifiers());
  }

  @Override
  public boolean isInstanceMemberClass() {
    return TypeProxyUtils.isInstanceMemberClass(typeBinding);
  }

  @Override
  public boolean isInstanceNestedClass() {
    return TypeProxyUtils.isInstanceNestedClass(typeBinding);
  }

  @Override
  public boolean isInterface() {
    return typeBinding != null && typeBinding.isInterface();
  }

  @Override
  public Node accept(Processor processor) {
    return Visitor_RegularTypeDescriptor.visit(processor, this);
  }

  @Override
  public boolean isJsType() {
    return isJsType;
  }

  @Override
  public boolean isJsFunctionInterface() {
    return isJsFunction;
  }

  @Override
  public boolean isJsFunctionImplementation() {
    return TypeProxyUtils.isJsFunctionImplementation(typeBinding);
  }

  @Override
  public MethodDescriptor getJsFunctionMethodDescriptor() {
    return TypeProxyUtils.getJsFunctionMethodDescriptor(typeBinding);
  }

  @Override
  public boolean isNative() {
    return isNative;
  }

  @Override
  public String getJsTypeNamespace() {
    return jsTypeNamespace;
  }

  @Override
  public String getJsTypeName() {
    return jsTypeName;
  }
}
