import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import scala.util.Random

object SimpleBankAccount {
  case class Deposit(amount: Double)
  case class Withdrawal(amount: Double)
  case object Statement
  case class TransactionSuccess(message: String)
  case class TransactionFailure(message: String)
}

class SimpleBankAccount(startingBalance: Double) extends Actor {
  import SimpleBankAccount._

  // Private Data and helper methods
  private val overdrawnLimit: Double = -50.0
  private var accountBalance: Double = this.startingBalance

  private def reachedOverdrawnLimit(transAmount: Double): Boolean = {
    this.accountBalance - transAmount <= this.overdrawnLimit         // Scala does require the "return" keyword for methods
  }
  private def reachedNegativeStatus(transAmount: Double): Boolean = {
    this.accountBalance - transAmount < 0.0 && maintainNegativeStatus(transAmount)
  }
  private def maintainNegativeStatus(transAmount: Double): Boolean = {
    this.accountBalance - transAmount >= this.overdrawnLimit
  }

  // Start in a positive account state as a default
  override def receive: Receive = positiveAccountState


  def positiveAccountState: Receive = {
    case Deposit(amount) =>
      if (amount < 0) {
        sender() ! TransactionFailure("Invalid deposit amount!")
      } else {
        this.accountBalance += amount
        sender() ! TransactionSuccess(s"$amount was successfully deposited")
      }
    case Withdrawal(amount) =>
      if (reachedNegativeStatus(amount)) {
        sender() ! TransactionSuccess(s"$amount was successfully withdrawn, (Negative balance warning!)")
        context.become(negativeAccountState) // Enter into a different state (negative balance after withdrawal)
      }
      else if (reachedOverdrawnLimit(amount)) {
        sender() ! TransactionFailure(s"Cannot withdraw: $amount, account will be overdrawn past the ${this.overdrawnLimit} limit")
        // We DO NOT change state, simply deny the transaction
      }
      else if (amount < 0) {
        sender() ! TransactionFailure("Invalid withdrawal amount!")
      } else {
        this.accountBalance -= amount
        sender() ! TransactionSuccess(s"$amount was successfully withdrawn")
      }
    case Statement => sender() ! s"Current Balance: ${this.accountBalance}"
  }

  def negativeAccountState: Receive = {
    case Withdrawal(amount) =>
      if (reachedOverdrawnLimit(amount)) {
        sender() ! TransactionFailure(s"Cannot withdraw: $amount, account is already overdrawn!")
      }
      else if (maintainNegativeStatus(amount)) {
        this.accountBalance -= amount
        sender() ! TransactionSuccess(s"$amount was successfully withdrawn, (Negative balance warning!)")
        // We are within our negative account limit but still with a negative balance
      }
      else if (amount < 0) {
        sender() ! TransactionFailure("Invalid withdrawal amount!")
      } else {
        this.accountBalance -= amount
        sender() ! TransactionSuccess(s"$amount was successfully withdrawn, (Negative balance warning!)")
      }
    case Deposit(amount) =>
      if (amount < 0) {
        sender() ! TransactionFailure("Invalid deposit amount!")
      }
      else if (this.accountBalance + amount >= 0.0) {
        this.accountBalance += amount
        sender() ! TransactionSuccess(s"$amount was successfully deposited")
        context.become(positiveAccountState)
        // The account balance is positive after the deposit so we change state to positive for the next message
      } else {
        this.accountBalance += amount
        sender() ! TransactionSuccess(s"$amount was successfully deposited")
      }
    case Statement => sender() ! s"Current Balance: ${this.accountBalance}"
  }
}

object AccountHolder {
  case class MakeDeposit(amount: Double, account: ActorRef)     // ActorRef is the actor address (cannot call by name)
  case class MakeWithdrawal(amount: Double, account: ActorRef)
  case class GetStatement(account: ActorRef)
}

class AccountHolder extends Actor {
  import SimpleBankAccount._
  import AccountHolder._

  override def receive: Receive = {
    case MakeDeposit(amount, account) => account ! Deposit(amount)
    case MakeWithdrawal(amount, account) => account ! Withdrawal(amount)
    case GetStatement(account) => account ! Statement
    case TransactionSuccess(message) => println(message)
    case TransactionFailure(message) => println(message)
    case message => println(message + "\n")
  }
}

object SimpleBankAccountTesting extends App {   // "extends App" is the scala of saying "public void main{} in Java"
  import AccountHolder._

  // Creates our actor system
  val accountSystem = ActorSystem("Bank")

  // Create our account actor
  val account = accountSystem.actorOf(Props(classOf[SimpleBankAccount], 100.0), "Account")

  // Create our account holder actor
  val user = accountSystem.actorOf(Props[AccountHolder], "User")

  // The user actor will send 5 deposit messages to its account actor (deposits between $1.00 - $100.00)
  for (i <- 1 to 5) {
    val seed = new Random().nextDouble()
    val random = 1.0 + (seed * (100.0 - 1.0))
    user ! MakeDeposit(random, account)
  }
  // Check our account balance
  user ! GetStatement(account)


  // The user actor will send 5 withdrawal messages to its account actor (withdrawals between $100.00 - $500.00)
  for (i <- 1 to 5) {
    val seed = new Random().nextDouble()
    val random = 100.0 + (seed * (500.0 - 100.0))
    user ! MakeWithdrawal(random, account)
  }
  // Check our account balance
  user ! GetStatement(account)

  // Shut down our actor system
  accountSystem.terminate()
}