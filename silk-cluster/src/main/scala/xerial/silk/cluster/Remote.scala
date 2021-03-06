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

//--------------------------------------
//
// Remote.scala
// Since: 2012/12/20 2:22 PM
//
//--------------------------------------

package xerial.silk.cluster

import xerial.core.log.Logger
import java.lang.reflect.InvocationTargetException
import xerial.silk.{Weaver, Silk}
import SilkClient.Run
import xerial.silk.cluster.closure.ClosureSerializer
import xerial.silk.framework.{NodeRef}
import xerial.silk.core.IDUtil


/**
 * Remote command launcher
 * @author Taro L. Saito
 */
object Remote extends IDUtil with Logger {


  /**
   * Run the given function at the specified host
   * @param ci
   * @param f
   * @tparam R
   * @return
   */
  def at[R](ci:NodeRef)(f: => R)(implicit weaver:Weaver): R = {
    weaver.runF0(locality=Seq(ci.name), f)
  }

  private[silk] def run(cb: ClassBox, r: Run) {
    debug(s"Running command at ${SilkCluster.localhost}")
    ClassBox.withClassLoader(cb.classLoader) {
      run(r.closure)
    }
  }

  private[silk] def run(closureBinary: Array[Byte]) {
    val closure = ClosureSerializer.deserializeClosure(closureBinary)
    val mainClass = closure.getClass
    trace(s"deserialized the closure: class $mainClass")
    for (m <- mainClass.getMethods.filter(mt => mt.getName == "apply" & mt.getParameterTypes.length == 0).headOption) {
      trace(s"invoke method: $m")
      try
        m.invoke(closure)
      catch {
        case e: InvocationTargetException => error(e.getTargetException)
      }
    }
  }

}