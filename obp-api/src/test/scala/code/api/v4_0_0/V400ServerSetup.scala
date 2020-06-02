package code.api.v4_0_0

import code.accountholders.AccountHolders
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createViewJson
import code.api.util.{APIUtil, ApiRole, ApiTrigger}
import code.api.util.APIUtil.OAuth.{Consumer, Token, _}
import code.api.util.ApiRole.{CanCreateAccountAttributeAtOneBank, CanCreateCustomer, CanCreateProduct, _}
import code.api.util.ErrorMessages.IncorrectTriggerName
import code.api.v1_2_1._
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.{BasicAccountsJSON, TransactionRequestBodyJsonV200}
import code.api.v2_1_0.{TransactionRequestWithChargeJSON210, TransactionRequestWithChargeJSONs210}
import code.api.v3_0_0.{CustomerAttributeResponseJsonV300, TransactionJsonV300, TransactionsJsonV300, ViewJsonV300}
import code.api.v3_1_0._
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import code.transactionrequests.MappedTransactionRequest
import com.openbankproject.commons.model.{AccountId, AmountOfMoneyJsonV121, BankId, BankIdAccountId, CreateViewJson, ErrorMessage, Transaction, TransactionRequest, UpdateViewJSON}
import dispatch.Req
import net.liftweb.json
import net.liftweb.json.Extraction
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers.{now, randomString}

import scala.collection.immutable
import scala.util.Random.nextInt

trait V400ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v4_0_0_Request: Req = baseRequest / "obp" / "v4.0.0"

  def randomBankId : String = {
    def getBanksInfo : APIResponse  = {
      val request = v4_0_0_Request / "banks"
      makeGetRequest(request)
    }
    val banksJson = getBanksInfo.body.extract[BanksJson400]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }
  def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v4_0_0_Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken)
    makeGetRequest(request)
  }

  def randomPrivateAccountId(bankId : String) : String = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[BasicAccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition).id
  }

  def randomPrivateAccount(bankId : String): AccountJSON = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }

  def randomOwnerViewPermalink(bankId: String, account: AccountJSON) : String = {
    val request = v4_0_0_Request / "banks" / bankId / "accounts" / account.id / "views" <@(consumer, token1)
    val reply = makeGetRequest(request)
    val possibleViewsPermalinks = reply.body.extract[ViewsJSONV121].views.filterNot(_.is_public==true).filter(_.id == CUSTOM_OWNER_VIEW_ID)
    val randomPosition = nextInt(possibleViewsPermalinks.size)
    possibleViewsPermalinks(randomPosition).id
  }

  def getTransactions(bankId : String, accountId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)], params: List[(String, String)] = Nil): APIResponse = {
    val request = v4_0_0_Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" <@(consumerAndToken)
    makeGetRequest(request, params)
  }

  def randomTransaction(bankId : String, accountId : String, viewId: String) : TransactionJsonV300 = {
    val transactionsJson = getTransactions(bankId, accountId, viewId, user1).body.extract[TransactionsJsonV300].transactions
    val randomPosition = nextInt(transactionsJson.size)
    transactionsJson(randomPosition)
  }
  
  def randomTransactionRequest(bankId : String, accountId : String, viewId: String, consumerAndToken: Option[(Consumer, Token)]) : TransactionRequestWithChargeJSON210 = {
    val request310 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / viewId / "transaction-requests").GET <@(consumerAndToken)
    val response310 = makeGetRequest(request310)
    response310.code should equal(200)
    val transactionRequests = response310.body.extract[TransactionRequestWithChargeJSONs210].transaction_requests_with_charges
    val randomPosition = nextInt(transactionRequests.size)
    transactionRequests(randomPosition)
  }
  
  def updateView(bankId: String, accountId: String, viewId: String, updateViewJson: UpdateViewJSON, consumerAndToken: Option[(Consumer, Token)]): ViewJsonV300 = {
    def putView(bankId: String, accountId: String, viewId : String, view: UpdateViewJSON, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).PUT <@(consumerAndToken)
      makePutRequest(request, write(view))
    }
    val reply = putView(bankId, accountId, viewId, updateViewJson, consumerAndToken)
    reply.body.extract[ViewJsonV300]
  }
  
  def createViewEndpoint(bankId: String, accountId: String, createViewJson: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): ViewJsonV300 = {
    def postView(bankId: String, accountId: String, view: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
      val request = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "views").POST <@(consumerAndToken)
      makePostRequest(request, write(view))
    }
    val reply = postView(bankId, accountId, createViewJson, consumerAndToken)
    org.scalameta.logger.elem(reply)
    reply.body.extract[ViewJsonV300]
  }

  def createProduct(bankId: String, code: String, json: PostPutProductJsonV310): ProductJsonV310 = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateProduct.toString)
    val request310 = (v4_0_0_Request / "banks" / bankId / "products" / code).PUT <@ (user1)
    val response310 = makePutRequest(request310, write(json))
    response310.code should equal(201)
    val product = response310.body.extract[ProductJsonV310]
    product.code shouldBe code
    product.parent_product_code shouldBe json.parent_product_code
    product.bank_id shouldBe bankId
    product.name shouldBe json.name
    product.category shouldBe json.category
    product.super_family shouldBe json.super_family
    product.family shouldBe json.family
    product.more_info_url shouldBe json.more_info_url
    product.details shouldBe json.details
    product.description shouldBe json.description
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    product
  }

  def createAccountAttribute(bankId: String, accountId: String, name: String, value: String, `type`: String): AccountAttributeResponseJson = {
    val postPutProductJsonV310 = PostPutProductJsonV310(
      name = "product name",
      parent_product_code = "",
      category = "category",
      family = "family",
      super_family = "super family",
      more_info_url = "www.example.com/prod1/more-info.html",
      details = "Details",
      description = "Description",
      meta = SwaggerDefinitionsJSON.metaJson
    )
    val product: ProductJsonV310 =
      createProduct(
        bankId=bankId,
        code=APIUtil.generateUUID(),
        json=postPutProductJsonV310
      )
    val accountAttributeJson = AccountAttributeJson(
      name = name,
      `type` = `type`,
      value = value
    )
    val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateAccountAttributeAtOneBank.toString)
    val requestCreate310 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId /
      "products" / product.code / "attribute").POST <@(user1)
    val responseCreate310 = makePostRequest(requestCreate310, write(accountAttributeJson))
    Then("We should get a 201")
    responseCreate310.code should equal(201)
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    responseCreate310.body.extract[AccountAttributeResponseJson]
  }
  
  // This will call create customer ,then return the customerId
  def createAndGetCustomerId (bankId:String, consumerAndToken: Option[(Consumer, Token)]) = {
    val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
    def createCustomer(consumerAndToken: Option[(Consumer, Token)]) ={
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val request310 = (v4_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 201")
      response310.code should equal(201)
      response310.body.extract[CustomerJsonV310]
    }
    createCustomer(consumerAndToken).customer_id
  }
  
  def createAndGetCustomerAttributeId(bankId:String, customerId:String, consumerAndToken: Option[(Consumer, Token)], postCustomerAttributeJson: Option[CustomerAttributeJsonV400] = None) = {
    lazy val postCustomerAttributeJsonV400 = postCustomerAttributeJson.getOrElse(SwaggerDefinitionsJSON.customerAttributeJsonV400)
    val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST <@ (user1)
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateCustomerAttributeAtOneBank.toString)
    val responseWithRole = makePostRequest(request400, write(postCustomerAttributeJsonV400))
    responseWithRole.body.extract[CustomerAttributeResponseJsonV300].customer_attribute_id
  }

  def createTransactionAttributeEndpoint(bankId:String, accountId:String, transactionId:String, consumerAndToken: Option[(Consumer, Token)]) = {
    lazy val postTransactionAttributeJsonV400 = SwaggerDefinitionsJSON.transactionAttributeJsonV400
    val request400 = (v4_0_0_Request / "banks" / bankId / "accounts"/ accountId /"transactions" / transactionId / "attribute").POST <@ (user1)
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateTransactionAttributeAtOneBank.toString)
    val responseWithRole = makePostRequest(request400, write(postTransactionAttributeJsonV400))
    responseWithRole.code should equal(201)
    responseWithRole.body.extract[TransactionAttributeResponseJson].transaction_attribute_id
  }

  def grantUserAccessToViewV400(bankId: String, 
                                accountId: String, 
                                userId: String, 
                                consumerAndToken: Option[(Consumer, Token)],
                                postBody: PostViewJsonV400
                               ): ViewJsonV300 = {
    val postJson = PostAccountAccessJsonV400(userId, postBody)
    val request = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "account-access" / "grant").POST <@ (consumerAndToken)
    val response = makePostRequest(request, write(postJson))
    Then("We should get a 201 and check the response body")
    org.scalameta.logger.elem(response.body)
    response.code should equal(201)
    response.body.extract[ViewJsonV300]
  }
  
  def createWebhookV400(bankId: String, 
                        accountId: String, 
                        userId: String, 
                        consumerAndToken: Option[(Consumer, Token)]): AccountWebhookJson = {
    val postJson = SwaggerDefinitionsJSON.accountWebhookPostJson
    val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, userId, CanCreateWebhook.toString)
    When("We make a request v3.1.0 with a Role " + canCreateWebhook)
    val request310 = (v4_0_0_Request / "banks" / bankId / "account-web-hooks").POST <@(consumerAndToken)
    val response310 = makePostRequest(request310, write(postJson.copy(account_id = accountId)))
    Then("We should get a 201")
    response310.code should equal(201)
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    response310.body.extract[AccountWebhookJson]
  }

  def postCommentForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, comment: PostTransactionCommentJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments").POST <@(consumerAndToken)
    makePostRequest(request, write(comment))
  }
  def createAccountEndpoint(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]) = {
    val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310
      .copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateAccount.toString)
    And("We make a request v4.0.0")
    val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" ).POST <@(consumerAndToken)
    val response400 = makePostRequest(request400, write(addAccountJson))
    Then("We should get a 201")
    response400.code should equal(201)
    val account = response400.body.extract[CreateAccountResponseJsonV310]
    account.account_id should not be empty
    account
  }

  def createTransactionRequestEndpoint(fromBankId: String,
                                       fromAccountId: String,
                                       fromCurrency: String,
                                       fromViewId: String,
                                       amount: String,
                                       toBankId: String,
                                       toAccountId: String,
                                       consumerAndToken: Option[(Consumer, Token)]) = {
    val toAccountJson = TransactionRequestAccountJsonV140(toBankId, toAccountId)
    val bodyValue = AmountOfMoneyJsonV121(fromCurrency, amount)
    val description = "Just test it!"
    val transactionRequestBody = TransactionRequestBodyJsonV200(toAccountJson, bodyValue, description)
    val createTransReqRequest = (v4_0_0_Request / "banks" / fromBankId / "accounts" / fromAccountId /
      fromViewId / "transaction-request-types" / "SANDBOX_TAN" / "transaction-requests").POST <@ (consumerAndToken)

    makePostRequest(createTransReqRequest, write(transactionRequestBody)).body.extract[TransactionRequestWithChargeJSON400]
  }

  def createTransactionRequestForDeleteCascade(bankId: String) = {
    // Create a Bank
    val bank = createBank(bankId)
    // Create from account
    val fromAccount = createAccountEndpoint(bank.bankId.value, "cascade_from_account", user1)
    // Create to account
    val toAccount = createAccountEndpoint(bank.bankId.value, "cascade_to_account", user1)
    // Create a custom view
    val customViewJson = createViewJson.copy(name = "_cascade_delete", metadata_view = "_cascade_delete", is_public = false)
    val customView = createViewEndpoint(bank.bankId.value, toAccount.account_id, customViewJson, user1)
    // Grant access to the view
    grantUserAccessToViewV400(
      bank.bankId.value,
      toAccount.account_id,
      resourceUser1.userId,
      user1,
      PostViewJsonV400(view_id = customView.id, is_system = false)
    )
    // Create a Transaction Request
    val transactionRequest = createTransactionRequestEndpoint(
      fromBankId = bank.bankId.value,
      fromAccountId = fromAccount.account_id,
      fromCurrency = fromAccount.balance.currency,
      fromViewId = customView.id,
      amount = "10",
      toBankId = bank.bankId.value,
      toAccountId = toAccount.account_id,
      user1
    )
    val transactionId = transactionRequest.transaction_ids.headOption.getOrElse("")
    val transactionAttributeId = createTransactionAttributeEndpoint(bank.bankId.value, fromAccount.account_id, transactionId, user1)
    val comment = postCommentForOneTransaction(
      bank.bankId.value,
      fromAccount.account_id,
      customView.id,
      transactionId,
      PostTransactionCommentJSON(randomString(5)),
      user1
    )
    (bank.bankId.value, fromAccount.account_id, transactionId)
  }
    
  
  
}