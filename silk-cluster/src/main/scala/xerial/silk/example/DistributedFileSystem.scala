//--------------------------------------
//
// DistributedFileSystem.scala
// Since: 2012/12/19 5:40 PM
//
//--------------------------------------

package xerial.silk.example

import java.io.File
import xerial.silk.framework.Host
import xerial.silk.cluster.SilkCluster


case class FileLoc(file:File, host:Host)

/**
 * Collect files in each hosts, then read files in the hosts where the file is located
 *
 * @author Taro L. Saito
 */
class DistributedFileSystem {

  def main(args:Array[String])  {

    implicit val weaver = SilkCluster.init

    val path = new File("/export/data")

    def listFiles(h:Host, p:File) : Seq[FileLoc] = {
      if(p.isDirectory)
        p.listFiles.flatMap { listFiles(h, _) }
      else if(p.exists())
        Seq(FileLoc(p, h))
      else
        Seq.empty
    }

    // Create list of files in each host
//    val fileList = weaver.hosts.flatMap { h => listFiles(h.host, path) }
//
//    val fs = fileList.iterator.toSeq
//    val file = fs.apply(0)
//    SilkCluster.at(file.host) {
//      // access to the file
//    }

  }

}