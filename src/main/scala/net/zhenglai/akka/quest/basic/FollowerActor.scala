package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorIdentity, ActorLogging, ActorRef, Identify, Props, Terminated }

class FollowerActor extends Actor with ActorLogging {
  val identifyId = 1

  context.actorSelection("/user/magicActorOut") ! Identify(identifyId)

  def active(another: ActorRef): Actor.Receive = {
    case Terminated(`another`) => context.stop(self)
  }

  override def receive = {
    case ActorIdentity(`identifyId`, Some(ref)) => // transform actorSelection => actorRef
      log.info("got {} for another actor", ref)
      context.watch(ref)
      context.become(active(ref))
    case ActorIdentity(`identifyId`, None) =>
      log.warning("another actor not found, stopping {}", self)
      context.stop(self)
  }
}

object FollowerActor {
  def props = Props(new FollowerActor)
}
