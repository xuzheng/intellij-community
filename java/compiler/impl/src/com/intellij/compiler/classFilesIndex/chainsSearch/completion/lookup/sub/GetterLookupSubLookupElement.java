/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub;

import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class GetterLookupSubLookupElement implements SubLookupElement {
  private final String myVariableName;
  private final String myMethodName;

  public GetterLookupSubLookupElement(final String methodName) {
    this(null, methodName);
  }

  public GetterLookupSubLookupElement(@Nullable final String variableName, final String methodName) {
    myVariableName = variableName;
    myMethodName = methodName;
  }

  @Override
  public void doImport(final PsiJavaFile javaFile) {
  }

  @Override
  public String getInsertString() {
    final StringBuilder sb = new StringBuilder();
    if (myVariableName != null) {
      sb.append(myVariableName).append(".");
    }
    sb.append(myMethodName).append("()");
    return sb.toString();
  }
}
