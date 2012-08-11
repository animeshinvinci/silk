/*
 * Copyright 2012 Taro L. Saito
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xerial.silk.text.parser

import annotation.tailrec
import xerial.core.log.Logging
import xerial.silk.text.parser.SilkExpr.SymbolExpr


//--------------------------------------
//
// SilkParser.scala
// Since: 2012/08/10 0:04
//
//--------------------------------------

/**
 * Grammar expressions  
 */
object SilkExpr {
  sealed abstract class ParseError extends Exception
  case class SyntaxError(posInLine: Int, message: String) extends ParseError
  case object NoMatch extends ParseError
  abstract class Parser {
    def LA1: SilkToken
    def consume: Parser
  }

  type ParseResult = Either[ParseError, Parser]

  sealed abstract class Expr[A] extends Logging {
    self =>
    trace("Define %s: %s", this.getClass.getSimpleName, toString)

    def ~(next: => Expr[A]): Expr[A] = new Expr[A] {
      def eval(in: Parser): ParseResult = self.eval(in).right.flatMap(next.eval(_))
    }

    def |(next: => Expr[A]): Expr[A] = new Expr[A] {
      def eval(in: Parser): ParseResult = {
        val ea = self.eval(in)
        ea match {
          case r@Right(_) => r
          case Left(NoMatch) => next.eval(in)
          case Left(_) => next.eval(in) match {
            case noMatch@Left(NoMatch) => noMatch
            case other => other
          }
        }
      }
    }
    def eval(in: Parser): ParseResult
  }

  case class SymbolExpr[A](t: TokenType) extends Expr[A] {
    def eval(in: Parser) = {
      val t = in.LA1
      Either.cond(t.tokenType == t, in.consume, NoMatch)
    }
  }

  implicit def expr(t: TokenType): SymbolExpr[SilkToken] = new SymbolExpr(t)
  /**
   * (expr (sep expr)*)?
   */
  def repeat[A](expr: => Expr[A], separator: TokenType): Expr[A] = new Expr[A] {
    private val r = option(expr ~ zeroOrMore(new SymbolExpr[A](separator) ~ expr))
    def eval(in: Parser) = r.eval(in)
  }

  def zeroOrMore[A](expr: => Expr[A]) = new Expr[A] {
    def eval(in: Parser) = {
      @tailrec def loop(p: Parser): ParseResult = {
        expr.eval(p) match {
          case Left(NoMatch) => Right(p)
          case l@Left(_) => l
          case Right(next) => loop(next)
        }
      }
      loop(in)
    }
  }

  def oneOrMore[A](expr: => Expr[A]) = new Expr[A] {
    def eval(in: Parser) = {
      @tailrec def loop(i: Int, p: Parser): ParseResult = {
        expr.eval(p) match {
          case Left(NoMatch) if i > 0 => Right(p)
          case l@Left(_) => l
          case Right(next) => loop(i + 1, next)
        }
      }
      loop(0, in)
    }
  }

  def oneOrMore[A](expr: => Expr[A], separator: TokenType): Expr[A] = new Expr[A] {
    val r = expr ~ zeroOrMore(new SymbolExpr[A](separator) ~ expr)
    def eval(in: Parser) = r.eval(in)
  }

  def option[A](expr: => Expr[A]): Expr[A] = new Expr[A] {
    def eval(in: Parser) = {
      expr.eval(in) match {
        case l@Left(NoMatch) => Right(in)
        case other => other
      }
    }
  }


}

object SilkParser {

  import Token._
  import SilkExpr._

  private class Parser(token: TokenStream) extends SilkExpr.Parser {
    def LA1 = token.LA(1)
    def consume = {
      token.consume; this
    }
  }

  def parse(expr: SilkExpr.Expr[SilkToken], silk: CharSequence) {
    val t = SilkLexer.tokenStream(silk)
    expr.eval(new Parser(t))
  }

  def parse(s: CharSequence) = {
    val t = SilkLexer.tokenStream(s)
    silk.eval(new Parser(t))
  }

  // Silk grammar
  type expr = Expr[SilkToken]
  def silk: expr = DataLine | node | preamble | LineComment | BlankLine

  def preamble: expr = Preamble ~ QName ~ option(preambleParams)
  def preambleParams: expr = (Separator ~ repeat(preambleParam, Comma)) | (LParen ~ repeat(preambleParam, Comma) ~ RParen)
  def preambleParam: expr = Name ~ option(Colon ~ preambleParamValue)
  def preambleParamValue: expr = value | typeName
  def typeName: expr = QName ~ option(LSquare ~ oneOrMore(QName, Comma) ~ RSquare)

  def node: expr = option(Indent) ~ Name ~ option(nodeParams) ~ option(nodeParamSugar | nodeParams)
  def nodeParamSugar: expr = Separator ~ repeat(param, Comma)
  def nodeParams: expr = LParen ~ repeat(param, Comma) ~ RParen ~ option(Colon ~ NodeValue)
  def param: expr = Name ~ option(Colon ~ value)

  def value: expr = Token.String | Integer | Real | QName | NodeValue | tuple
  def tuple: expr = LParen ~ repeat(value, Comma) ~ RParen

}


/**
 * @author leo
 */
class SilkParser(token: TokenStream) {

  import Token._
  import SilkElement._
  import SilkParser._

  private def LA1 = token.LA(1)
  private def LA2 = token.LA(2)
  private def consume = token.consume


}