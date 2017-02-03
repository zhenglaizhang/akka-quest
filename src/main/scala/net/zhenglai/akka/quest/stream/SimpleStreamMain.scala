package net.zhenglai.akka.quest.stream

import java.nio.file.Paths

import scala.concurrent.{ Await, Future }

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.{ FileIO, Flow, Keep, Sink, Source }
import akka.stream.{ ActorMaterializer, IOResult }
import akka.util.ByteString
import scala.concurrent.duration._

object SimpleStreamMain extends App {
  // NotUsed: auxiliary value
  implicit val system: ActorSystem = ActorSystem("SimpleStreamSystem")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  val log = Logging(system, getClass)

  val source: Source[Int, NotUsed] = Source(1 to 100)

  // The Materializer is a factory for stream execution engines, it is the thing that makes streams run

  // the Source is just a description of what you want to run, and like an architect’s blueprint it can be reused, incorporated into a larger design.
  //  source.runFold(0)(_ + _).foreach(i => print(s"sum: $i"))
  //  source.runForeach(i => log.info(i.toString))


  def lineSink(fileName: String): Sink[String, Future[IOResult]] =
    Flow[String] // left: NotUsed
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(Paths.get(fileName)))(Keep.right)

  val factorials = source.scan(BigInt(1))((acc, next) => {log.info("cur: {}", acc); acc * next})
  val result: Future[IOResult] =
    factorials
      .map(num => ByteString(s"$num\n"))
      .runWith(FileIO.toPath(Paths.get("factorials.txt")))
  result.foreach(r => log.info("io result: {}", r))

  factorials
    .map(_.toString)
    .runWith(lineSink("factorials2.txt"))

  Await.ready(result, 10.seconds)
  mat.shutdown()
  system.terminate()
}
