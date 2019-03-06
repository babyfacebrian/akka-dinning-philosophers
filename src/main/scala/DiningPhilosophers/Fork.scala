package DiningPhilosophers

import akka.actor.{Actor, ActorLogging, ActorRef}

// Domain of the DiningPhilosophers.Fork Actor
object Fork {
  case object PickUp
  case object PutDown
  case object ForkInUse
  case object ForkTaken
}

class Fork extends Actor with ActorLogging {
  import Fork._
  def name: String = self.path.name

  /* Fork receive message handlers */
  override def receive: Receive = forkIsAvailable   // Start as available

  def forkIsAvailable: Receive = {
    case PickUp =>
      sender() ! ForkTaken    // picked up by sending actor
      context.become(takenByPhilosopher(sender()))
  }

  def takenByPhilosopher(philosopher: ActorRef): Receive = {
    case PickUp =>
      sender() ! ForkInUse    // Deny being picked up, still in use
    case PutDown if sender() == philosopher =>
      context.become(forkIsAvailable)   // once put down become available again
  }
}
