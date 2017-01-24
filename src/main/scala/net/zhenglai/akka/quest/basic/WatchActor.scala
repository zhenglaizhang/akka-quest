package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging, ActorRef, Props, Terminated }

class WatchActor(target: ActorRef) extends Actor with ActorLogging {
  //  val child = context.actorOf(Props.empty, "child")
  context.watch(target) // lifecycle monitoring aka DeathWatch registration
  // context.unwatch(child)

  // Terminated message is generated independent of the order in which registration and termination occur
  // registering for monitoring of an already terminated actor leads to the immediate generation of the Terminated message.
  // Registering multiple times does not necessarily lead to multiple messages being generated,
  // but there is no guarantee that only exactly one such message is received
  var lastSender = context.system.deadLetters

  override def receive = {
    case "kill" =>
      context.stop(target)
      lastSender = sender()
    case Terminated(`target`) =>
      log.info("target terminated")
      lastSender ! "finished"

  }
}

object WatchActor {
  def props(target: ActorRef) = Props(classOf[WatchActor], target)
}
