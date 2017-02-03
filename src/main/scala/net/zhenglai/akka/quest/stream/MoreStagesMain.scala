package net.zhenglai.akka.quest.stream

import scala.concurrent.Await

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import scala.concurrent.duration._

object MoreStagesMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val mat: ActorMaterializer = ActorMaterializer()

//  cycle




  mat.shutdown()
  system.terminate()
  sys.exit(0)


  private def cycle = {
    val s1 = Source.cycle(() => Seq(1, 2, 3).toIterator).runForeach(println)
    Await.ready(s1, 1 second)
  }
}
