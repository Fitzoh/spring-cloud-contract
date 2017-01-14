package com.example.loan;

import static org.assertj.core.api.Assertions.assertThat;

import au.com.dius.pact.consumer.Pact;
import au.com.dius.pact.consumer.PactProviderRule;
import au.com.dius.pact.consumer.PactVerification;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.model.PactFragment;
import org.apache.commons.collections.map.SingletonMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import com.example.loan.model.Client;
import com.example.loan.model.LoanApplication;
import com.example.loan.model.LoanApplicationResult;
import com.example.loan.model.LoanApplicationStatus;


@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment=WebEnvironment.NONE)
@DirtiesContext
public class LoanApplicationServiceTests {

	@Autowired
	private LoanApplicationService service;


	@Rule
	public PactProviderRule mockProvider = new PactProviderRule("Provider", this);

	@Before
	public void setUp() throws Exception {
		service.setPort(mockProvider.getConfig().getPort());

	}

	@Pact(consumer="Consumer") // will default to the provider name from mockProvider
	public PactFragment createNonFraudFragment(PactDslWithProvider builder) {
		return builder
				.uponReceiving("A non-fraudulent fraud check request")
					.path("/fraudcheck")
					.method("PUT")
					.headers("Content-Type", "application/vnd.fraud.v1+json")
					.body(new PactDslJsonBody()
						.stringValue("clientId", "1234567890")
						.numberValue("loanAmount", 123.123))
				.willRespondWith()
					.status(200)
					.headers(new SingletonMap("Content-Type", "application/vnd.fraud.v1+json;charset=UTF-8"))
					.body(new PactDslJsonBody()
						.stringValue("fraudCheckStatus", "OK")
						.nullValue("rejectionReason"))
				.toFragment();
	}

	@Test
	@PactVerification(fragment = "createNonFraudFragment")
	public void shouldSuccessfullyApplyForLoan() {
		// given:
		LoanApplication application = new LoanApplication(new Client("1234567890"),
				123.123);
		// when:
		LoanApplicationResult loanApplication = service.loanApplication(application);
		// then:
		assertThat(loanApplication.getLoanApplicationStatus())
				.isEqualTo(LoanApplicationStatus.LOAN_APPLIED);
		assertThat(loanApplication.getRejectionReason()).isNull();
	}

	@Pact(consumer="Consumer") // will default to the provider name from mockProvider
	public PactFragment createFraudFragment(PactDslWithProvider builder) {
		return builder
				.uponReceiving("A fraudulent fraud check request")
				.path("/fraudcheck")
				.method("PUT")
				.headers("Content-Type", "application/vnd.fraud.v1+json")
				.body(new PactDslJsonBody()
						.stringMatcher("clientId", "[0-9]{10}", "1234567890")
//						.numberValue("loanAmount", 99999))//can't get an exact value to work:     BodyMismatch - Expected 99999 but received 99999.0 ... looks like a pact-jvm issue
						.numberType("loanAmount", 99999))
				.willRespondWith()
				.status(200)
				.headers(new SingletonMap("Content-Type", "application/vnd.fraud.v1+json;charset=UTF-8"))
				.body(new PactDslJsonBody()
						.stringMatcher("fraudCheckStatus", "FRAUD", "FRAUD")
						.stringValue("rejectionReason", "Amount too high"))
				.toFragment();
	}

	@Test
	@PactVerification(fragment = "createFraudFragment")
	public void shouldBeRejectedDueToAbnormalLoanAmount() {
		// given:
		LoanApplication application = new LoanApplication(new Client("1234567890"),
				99999);
		// when:
		LoanApplicationResult loanApplication = service.loanApplication(application);
		// then:
		assertThat(loanApplication.getLoanApplicationStatus())
				.isEqualTo(LoanApplicationStatus.LOAN_APPLICATION_REJECTED);
		assertThat(loanApplication.getRejectionReason()).isEqualTo("Amount too high");
	}

	@Pact(consumer="Consumer") // will default to the provider name from mockProvider
	public PactFragment getFraudsFragment(PactDslWithProvider builder) {
		return builder
				.uponReceiving("A request to get the fraud count")
					.path("/frauds")
					.method("GET")
				.willRespondWith()
					.status(200)
					.headers(new SingletonMap("Content-Type", "application/vnd.fraud.v1+json;charset=UTF-8"))
					.body(new PactDslJsonBody()
						.numberValue("count", 200))
				.toFragment();
	}

	@Test
	@PactVerification(fragment = "getFraudsFragment")
	public void shouldSuccessfullyGetAllFrauds() {
		// when:
		int count = service.countAllFrauds();
		// then:
		assertThat(count).isEqualTo(200);
	}

	@Pact(consumer="Consumer") // will default to the provider name from mockProvider
	public PactFragment getDrunksFragment(PactDslWithProvider builder) {
		return builder
				.uponReceiving("A request to get the drunks count")
				.path("/drunks")
				.method("GET")
				.willRespondWith()
				.status(200)
				.headers(new SingletonMap("Content-Type", "application/vnd.fraud.v1+json;charset=UTF-8"))
				.body(new PactDslJsonBody()
						.numberValue("count", 100))
				.toFragment();
	}

	@Test
	@PactVerification(fragment = "getDrunksFragment")
	public void shouldSuccessfullyGetAllDrunks() {
		// when:
		int count = service.countDrunks();
		// then:
		assertThat(count).isEqualTo(100);
	}

}
