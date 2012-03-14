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

package xerial.silk.util.io

//--------------------------------------
//
// PageInputStream.scala
// Since: 2012/03/14 10:33
//
//--------------------------------------

object PageInputStream {
  val DefaultPageSize: Int = 8192
}

import PageInputStream._
import java.io.{File, FileInputStream, FileReader, Reader, InputStream}


trait PagedInput[T] extends RichInput[T] with Iterable[Array[T]]  {
  var reachedEOF = false
  val pageSize: Int
  def newArray(size:Int) : Array[T]

  def readNextPage(pageSize:Int): Array[T] = {
    val page = newArray(pageSize)
    val readLen = readFully(page)
    if(readLen < pageSize)
      reachedEOF = true

    if (readLen <= 0) {
      null
    }
    else if (readLen < pageSize)
      page.slice(0, readLen)
    else
      page
  }

  override def foreach[U](f: (Array[T]) => U) {
    def loop: Unit = {
      val page = readNextPage(pageSize)
      if (page != null) {
        f(page)
        loop
      }
      loop
    }
  }

  override def toArray[B >: Array[T] : ClassManifest]: Array[B] = {
    /*
     Overriding this method is necessary since [[scala.collection.TraversableOnce.toArray]]
      wrongly set isTraversableAgain = true but page reader cannot be traverse more than once
      */
    iterator.toArray
  }


  def iterator: Iterator[Array[T]] = new PageIterator

  /**
   * Base implementation of page iterator
   */
  class PageIterator extends Iterator[Array[T]] {
    private var current: Array[T] = null

    def hasNext = {
      if (current != null)
        true
      else if (reachedEOF)
        false
      else {
        current = readNextPage(pageSize)
        current != null
      }
    }

    def next: Array[T] = {
      if (hasNext) {
        val e = current
        current = null
        e
      }
      else
        Iterator.empty.next
    }
  }


}


/**
 * Page-wise input stream reader
 *
 * @author leo
 */
class PageInputStream(protected val in: InputStream, byteSize: Int) extends PagedInput[Byte] with RichInputStream {
  val pageSize = byteSize

  def this(in: InputStream) = this(in, byteSize = DefaultPageSize)

  def this(file: File, byteSize: Int = DefaultPageSize) = this(new FileInputStream(file))
}

/**
 * Page-wise text reader
 * @param in
 * @param numCharsInPage
 */
class PageReader(protected val in: Reader, numCharsInPage: Int) extends PagedInput[Char] with RichReader {
  val pageSize = numCharsInPage

  def this(in: Reader) = this(in, numCharsInPage = DefaultPageSize)

  def this(file: File, numCharsInPage: Int = DefaultPageSize) = this(new FileReader(file))

}