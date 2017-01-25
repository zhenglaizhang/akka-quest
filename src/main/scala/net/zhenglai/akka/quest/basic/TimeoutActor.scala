package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props, ReceiveTimeout }
import scala.concurrent.duration._

// TODO: more...
class TimeoutActor extends Actor with ActorLogging {
  // defines the inactivity timeout after which the sending of a ReceiveTimeout message is triggered
  context.setReceiveTimeout(30 milliseconds)

   // the receive timeout might fire and enqueue the ReceiveTimeout message right after another message was enqueued;
  // hence it is not guaranteed that upon reception of the receive timeout there must have been an idle period beforehand as configured via this method.

  // Messages marked with NotInfluenceReceiveTimeout will not reset the timer.
  // This can be useful when ReceiveTimeout should be fired by external inactivity but not influenced by internal activity, e.g. scheduled tick messages.

  override def receive = {
    case "hello" =>
      log.info("Receive hello")
      context.setReceiveTimeout(300 milliseconds)
    case ReceiveTimeout =>
      // turn off
      log.info("receiving timeout...")
      context.setReceiveTimeout(Duration.Undefined)
      throw new RuntimeException("Receive timed out")
  }
}

object ReceiveTimeoutActorMain extends App {
  val system = ActorSystem("ReceiveTimeoutActorMain")

  val actor = system.actorOf(Props(new TimeoutActor), name = "receiveTimeoutActor")


  actor ! "hello"

  Thread.sleep(1000)


  system.terminate()
}
