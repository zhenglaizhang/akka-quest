package net.zhenglai.akka.quest.stream

import scala.concurrent.Await

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, DelayOverflowStrategy, KillSwitches }
import akka.stream.scaladsl.{ Keep, Sink, Source }
import scala.concurrent.duration._

object KillSwitchMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val countingSrc = Source(Stream.from(1))
    .delay(1 second, DelayOverflowStrategy.backpressure)
  val lastSink = Sink.last[Int]

  // UniqueKillSwitch allows to control the completion of
  // one materialized Graph of FlowShape
  val (killSwitch, last) = countingSrc
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(lastSink)(Keep.both)
    .run()

  Thread.sleep(2000)

  killSwitch.shutdown()

  println("last: " + Await.result(last, 1 second))


  val (killSwitch1, last1) = countingSrc
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(lastSink)(Keep.both)
    .run()

  val error = new RuntimeException("boom!")
  killSwitch1.abort(error)


  Thread.sleep(2000)
  println("last1: " + Await.result(last1.failed, 2 second))


  // A SharedKillSwitch allows to control the completion of an arbitrary number graphs of FlowShape.
  // It can be materialized multiple times via its flow method, and all materialized graphs linked to it are controlled by the switch.
  val sharedKillSwitch = KillSwitches.shared("my-kill-switch")
  val last2 = countingSrc
    .via(sharedKillSwitch.flow)
    .runWith(lastSink)

  val delayedLast = countingSrc
    .delay(1 second, DelayOverflowStrategy.backpressure)
    .via(sharedKillSwitch.flow)
    .runWith(lastSink)
  Thread.sleep(2000)
  sharedKillSwitch.shutdown()
  println(s"last2 = ${Await.result(last2, 3 seconds)}")
  println(s"delayedLast = ${Await.result(delayedLast, 5 seconds)}")


  // sharedKillSwitch.abort(error)


  // A UniqueKillSwitch is always a result of a materialization,
  // whilst SharedKillSwitch needs to be constructed before any materialization takes place.

  system.terminate()
}
