package DiningPhilosophers

import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, FiniteDuration}

// Domain for DiningPhilosophers.Philosopher
object Philosopher {
  case object Eat
  case object Think
  case object MealEaten
  case object ContinueEating
}

class Philosopher(private val leftFork: ActorRef, private val rightFork: ActorRef) extends Actor with ActorLogging {
  import Fork._
  import Philosopher._

  def name: String = self.path.name
  implicit private val executionContext: ExecutionContext = context.dispatcher
  private val eatingTime = 1.seconds
  private val thinkingTime = 3.seconds
  private val retryTime = 10.millis

  private def takeForks(): Unit = {
    this.leftFork ! PickUp
    this.rightFork ! PickUp
  }

  private def releaseForks(): Unit = {
    this.leftFork ! PutDown
    this.rightFork ! PutDown
  }

  private def backToThinking(time: FiniteDuration): Unit = {
    context.system.scheduler.scheduleOnce(time, self, Eat)
    context.become(Thinking)
  }

  /* Philosopher receive message handlers */
  override def receive: Receive = Thinking    // GOTO THINKING

  def Thinking: Receive = {
    case Think =>
      log.info(s"${this.name} STARTING TO THINK...")
      context.system.scheduler.scheduleOnce(thinkingTime, self, Eat)
    case Eat =>
      takeForks()
      context.become(Hungry)    // GOTO HUNGRY
  }

  def Hungry: Receive = {
    case ForkInUse =>
      releaseForks()
      backToThinking(retryTime)   // GOTO THINKING
    case ForkTaken =>
      context.become(Waiting)   // GOTO WAITING
  }

  def Waiting: Receive = {
    case ForkInUse =>
      releaseForks()
      backToThinking(retryTime)   // GOTO THINKING
    case ForkTaken =>
      log.debug(s"${this.name} EATING WITH ${this.leftFork.path.name}, ${this.rightFork.path.name}")
      context.system.scheduler.scheduleOnce(eatingTime, self, Eat)
      context.become(Eating)    // GOTO EATING
  }

  def Eating: Receive = {
    case Eat =>
      log.debug(s"${this.name} PUTTING DOWN ${this.leftFork.path.name}, ${this.rightFork.path.name}")
      releaseForks()
      context.parent ! MealEaten
    case ContinueEating =>
      backToThinking(thinkingTime)    // GOTO THINKING
  }
}
