package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props, Stash }


class StashActor extends Actor with ActorLogging with Stash {
  override def receive = {
    case "open" =>
      unstashAll()
      context.become({
        case "write" => log.info("writing ...")
        case "close" =>
          log.info("open.close.unstashing")
          unstashAll()
          context.unbecome()
        case msg =>
          log.info("open.stashing: {}", msg)
          stash()
      }, discardOld = false)
    case msg =>
      log.info("stashing: {}", msg)
      stash() // Invoking stash() adds the current message (the message that the actor received last) to the actor's stash
  }
}

object StashActorMain extends App {

  val system = ActorSystem(name = "stashActorSystem")
  val actor = system.actorOf(Props[StashActor], name = "stashActor")
  actor ! "wow"
  actor ! "write"
  actor ! "open"
  actor ! "close"

  Thread.sleep(100)
  system.terminate()
}
