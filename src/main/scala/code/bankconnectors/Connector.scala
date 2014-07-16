package code.bankconnectors

import net.liftweb.common.Box
import code.model.Bank
import code.model.BankAccount
import net.liftweb.util.SimpleInjector
import code.model.User
import code.model.ModeratedOtherBankAccount
import code.model.OtherBankAccount
import code.model.ModeratedTransaction
import code.model.Transaction
import java.util.Date

object Connector  extends SimpleInjector {

  val connector = new Inject(buildOne _) {}
  
  def buildOne: Connector = LocalConnector
  
}

class OBPQueryParam
trait OBPOrder { def orderValue : Int }
object OBPOrder {
  def apply(s: Option[String]): OBPOrder = s match {
    case Some("asc") => OBPAscending
    case _ => OBPDescending
  }
}
object OBPAscending extends OBPOrder { def orderValue = 1 }
object OBPDescending extends OBPOrder { def orderValue = -1}
case class OBPLimit(value: Int) extends OBPQueryParam
case class OBPOffset(value: Int) extends OBPQueryParam
case class OBPFromDate(value: Date) extends OBPQueryParam
case class OBPToDate(value: Date) extends OBPQueryParam
case class OBPOrdering(field: Option[String], order: OBPOrder) extends OBPQueryParam

trait Connector {
  
  //gets a particular bank handled by this connector
  def getBank(permalink : String) : Box[Bank]
  
  //gets banks handled by this connector
  def getBanks : List[Bank]
  
  def getBankAccount(bankPermalink : String, accountId : String) : Box[BankAccount]
  
  def getAllPublicAccounts : List[BankAccount]
  
  def getPublicBankAccounts(bank : Bank) : List[BankAccount]
  
  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccount]
  
  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : Box[List[BankAccount]]
  
  def getNonPublicBankAccounts(user : User) : Box[List[BankAccount]]
  
  def getNonPublicBankAccounts(user : User, bankID : String) : Box[List[BankAccount]]
  
  def getModeratedOtherBankAccount(bankID: String, accountID : String, otherAccountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]) : Box[ModeratedOtherBankAccount]
  
  def getModeratedOtherBankAccounts(bankID: String, accountID : String)
  	(moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[List[ModeratedOtherBankAccount]]
  
  //TODO: Move OBPQueryParam out of com.dataAccess.OBPEnvelope into a more general package
  def getModeratedTransactions(permalink: String, bankPermalink: String, queryParams: OBPQueryParam*)
    (moderate: Transaction => ModeratedTransaction): Box[List[ModeratedTransaction]]
  
  def getModeratedTransaction(id : String, bankPermalink : String, accountPermalink : String)
    (moderate: Transaction => ModeratedTransaction) : Box[ModeratedTransaction]

  
  //...
}