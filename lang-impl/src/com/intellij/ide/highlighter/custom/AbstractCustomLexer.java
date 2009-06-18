package com.intellij.ide.highlighter.custom;

import com.intellij.ide.highlighter.custom.tokens.TokenInfo;
import com.intellij.ide.highlighter.custom.tokens.TokenParser;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;

/**
 * @author dsl
 */
public abstract class AbstractCustomLexer extends LexerBase {
  protected CharSequence myBuffer = ArrayUtil.EMPTY_CHAR_SEQUENCE;
  protected int myStartOffset = 0;
  protected int myEndOffset = 0;
  private final TokenParser[] myTokenParsers;
  private TokenInfo myCurrentToken;
  private int myPosition;

  public AbstractCustomLexer(TokenParser[] tokenParsers) {
    myTokenParsers = tokenParsers;

    int smartUpdateShift = 0;
    for (TokenParser tokenParser : myTokenParsers) {
      smartUpdateShift = Math.max(smartUpdateShift, tokenParser.getSmartUpdateShift());
    }
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPosition = myStartOffset;
    myCurrentToken = new TokenInfo();
    for (TokenParser tokenParser : myTokenParsers) {
      tokenParser.setBuffer(myBuffer, myStartOffset, myEndOffset);
    }
    advance();
  }

  public int getState() {
    return 0;
  }

  public IElementType getTokenType() {
    return myCurrentToken.getType();
  }

  public int getTokenStart() {
    return myCurrentToken.getStart();
  }

  public int getTokenEnd() {
    return myCurrentToken.getEnd();
  }

  public void advance() {
    if (myPosition >= myEndOffset) {
      myCurrentToken.updateData(myPosition, myPosition, null);
      return;
    }
    boolean tokenFound = false;
    for (TokenParser tokenParser : myTokenParsers) {
      if (tokenParser.hasToken(myPosition)) {
        tokenParser.getTokenInfo(myCurrentToken);
        tokenFound = true;
        break;
      }
    }

    if (!tokenFound) {
      myCurrentToken.updateData(myPosition, myPosition + 1, CustomHighlighterTokenType.CHARACTER);
    }
    myPosition = myCurrentToken.getEnd();
  }

  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myEndOffset;
  }

}
