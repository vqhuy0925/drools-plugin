// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.plugins.drools.lang.support;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.plugins.drools.lang.highlight.DroolsSyntaxHighlighter;
import com.intellij.plugins.drools.lang.lexer.DroolsTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DroolsEditorHighlighter extends LayeredLexerEditorHighlighter{
  private final @Nullable VirtualFile myVirtualFile;

  public DroolsEditorHighlighter(final @Nullable Project project,
                                 final @Nullable VirtualFile virtualFile,
                                 final @NotNull EditorColorsScheme colors) {
    super(new DroolsSyntaxHighlighter(), colors);
    myVirtualFile = virtualFile;
    registerLayer(DroolsTokenTypes.JAVA_STATEMENT, new LayerDescriptor(new JavaFileHighlighter(), ""));
  }
}
