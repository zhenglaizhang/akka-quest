package net.zhenglai.akka.quest.stream

import scala.concurrent.Await

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import scala.concurrent.duration._

object MoreStagesMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val mat: ActorMaterializer = ActorMaterializer()


  flatten

  Thread.sleep(1000)
  mat.shutdown()
  system.terminate()
  sys.exit(0)


  private def cycle = {
    val s1 = Source.cycle(() => Seq(1, 2, 3).toIterator).runForeach(println)
    Await.ready(s1, 1 second)
  }

  private[this] def flatten = {
    Source.single(List(1, 2, 3))
      .map { x => println(s"x: $x"); x }
      .mapConcat(_.toList)
      .map { x => println(s"x: $x"); x }
      .runWith(Sink.ignore)
  }
}
