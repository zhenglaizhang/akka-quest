package net.zhenglai.akka.quest.stream

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Tcp.{ IncomingConnection, ServerBinding }
import akka.stream.scaladsl.{ Flow, Framing, Sink, Source, Tcp }
import akka.util.ByteString

object IOMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  conn()

  private[this] def echo() = {
    val binding: Future[ServerBinding] =
      Tcp()
        .bind("127.0.0.1", 8889)
        .to(Sink.ignore)
        .run()

    binding.map { b =>
      b.unbind() onComplete (_ => println("unbind-ed"))
    }
  }

  // TODO: understand it
  private[this] def conn() = {
    val connections: Source[IncomingConnection, Future[ServerBinding]] =
      Tcp().bind("127.0.0.1", 9999)

    connections runForeach { connection =>
      // we simply handle each incoming connection using a Flow
      // which will be used as the processing stage to handle
      // and emit ByteString s from and to the TCP Socket
      println(s"New connection from: ${connection.remoteAddress}")
      val echo = Flow[ByteString]
        .via(Framing.delimiter(
          ByteString("\n"),
          maximumFrameLength = 256,
          // The last boolean argument indicates that we require an
          // explicit line ending even for the last message before
          // the connection is closed.
          allowTruncation = true
        ))
        .map(_.utf8String)
        .map(_ + "!!!\n")
        .map { x => println(x); x }
        .map(ByteString(_))

      // echo the received text (appended with !!!\n) to clients
      // incoming connection flow is not freely shareable
      // it directly corresponds to an existing,
      // already accepted connection its handling can only
      // ever be materialized once.
      connection.handleWith(echo)
    }

    // Closing connections is possible by cancelling the incoming
    // connection Flow from your server logic
    // (e.g. by connecting its downstream to a Sink.cancelled
    // and its upstream to a Source.empty).
    // It is also possible to shut down the server's socket by cancelling
    // the IncomingConnection source connections.
  }

  // todo: http://doc.akka.io/docs/akka/2.4/scala/stream/stream-io.html

  //  Thread.sleep(100)
  //  system.terminate()
}
