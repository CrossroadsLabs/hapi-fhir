package ca.uhn.fhir.jpa.interceptor.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.svc.IIdHelperService;
import ca.uhn.fhir.jpa.searchparam.registry.SearchParameterCanonicalizer;
import ca.uhn.fhir.jpa.searchparam.submit.interceptor.SearchParamValidatingInterceptor;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.storage.ResourcePersistentId;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.SearchParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SearchParameterValidatingInterceptorTest {

	static final FhirContext ourFhirContext = FhirContext.forR4();

	@Mock
	RequestDetails myRequestDetails;

	@Mock
	IFhirResourceDao myIFhirResourceDao;

	@Mock
	DaoRegistry myDaoRegistry;
	@Mock
	IIdHelperService myIdHelperService;

	SearchParamValidatingInterceptor mySearchParamValidatingInterceptor;

	SearchParameter mySearchParameterId1;

	static String ID1 = "ID1";
	static String ID2 = "ID2";

	@BeforeEach
	public void beforeEach(){

		mySearchParamValidatingInterceptor = new SearchParamValidatingInterceptor();
		mySearchParamValidatingInterceptor.setFhirContext(ourFhirContext);
		mySearchParamValidatingInterceptor.setSearchParameterCanonicalizer(new SearchParameterCanonicalizer(ourFhirContext));
		mySearchParamValidatingInterceptor.setIIDHelperService(myIdHelperService);
		mySearchParamValidatingInterceptor.setDaoRegistry(myDaoRegistry);

		mySearchParameterId1 = aSearchParameter(ID1);

	}

	@Test
	public void whenValidatingInterceptorCalledForNonSearchParamResoucre_thenIsAllowed(){
		Patient patient = new Patient();

		mySearchParamValidatingInterceptor.resourcePreCreate(patient, null);
		mySearchParamValidatingInterceptor.resourcePreUpdate(null, patient, null);
	}

	@Test
	public void whenCreatingNonOverlappingSearchParam_thenIsAllowed(){
		when(myDaoRegistry.getResourceDao(eq(SearchParamValidatingInterceptor.SEARCH_PARAM))).thenReturn(myIFhirResourceDao);

		setPersistedSearchParameters(emptyList());

		SearchParameter newSearchParam = aSearchParameter(ID1);

		mySearchParamValidatingInterceptor.resourcePreCreate(newSearchParam, myRequestDetails);

	}

	@Test
	public void whenCreatingOverlappingSearchParam_thenExceptionIsThrown(){
		when(myDaoRegistry.getResourceDao(eq(SearchParamValidatingInterceptor.SEARCH_PARAM))).thenReturn(myIFhirResourceDao);

		setPersistedSearchParameters(asList(mySearchParameterId1));

		SearchParameter newSearchParam = aSearchParameter(ID2);

		try {
			mySearchParamValidatingInterceptor.resourcePreCreate(newSearchParam, myRequestDetails);
			fail();
		}catch (UnprocessableEntityException e){
			assertTrue(e.getMessage().contains("2131"));
		}

	}

	@Test
	public void whenUsingPutOperationToCreateNonOverlappingSearchParam_thenIsAllowed(){
		when(myDaoRegistry.getResourceDao(eq(SearchParamValidatingInterceptor.SEARCH_PARAM))).thenReturn(myIFhirResourceDao);

		setPersistedSearchParameters(emptyList());

		SearchParameter newSearchParam = aSearchParameter(ID1);

		mySearchParamValidatingInterceptor.resourcePreUpdate(null, newSearchParam, myRequestDetails);
	}

	@Test
	public void whenUsingPutOperationToCreateOverlappingSearchParam_thenExceptionIsThrown(){
		when(myDaoRegistry.getResourceDao(eq(SearchParamValidatingInterceptor.SEARCH_PARAM))).thenReturn(myIFhirResourceDao);

		setPersistedSearchParameters(asList(mySearchParameterId1));

		SearchParameter newSearchParam = aSearchParameter(ID2);

		try {
			mySearchParamValidatingInterceptor.resourcePreUpdate(null, newSearchParam, myRequestDetails);
			fail();
		}catch (UnprocessableEntityException e){
			assertTrue(e.getMessage().contains("2125"));
		}
	}

	@Test
	public void whenUpdateSearchParam_thenIsAllowed(){
		when(myDaoRegistry.getResourceDao(eq(SearchParamValidatingInterceptor.SEARCH_PARAM))).thenReturn(myIFhirResourceDao);

		setPersistedSearchParameters(asList(mySearchParameterId1));
		when(myIdHelperService.translatePidsToFhirResourceIds(any())).thenReturn(Set.of(mySearchParameterId1.getId()));


		SearchParameter newSearchParam = aSearchParameter(ID1);

		mySearchParamValidatingInterceptor.resourcePreUpdate(null, newSearchParam, myRequestDetails);

	}

	private void setPersistedSearchParameters(List<SearchParameter> theSearchParams){
		List<ResourcePersistentId> resourcePersistentIds = theSearchParams
			.stream()
			.map(SearchParameter::getId)
			.map(theS -> new ResourcePersistentId(theS))
			.collect(Collectors.toList());
		Set<String> ids = theSearchParams.stream().map(sp -> sp.getId()).collect(Collectors.toSet());

		when(myIFhirResourceDao.searchForIds(any(), any())).thenReturn(resourcePersistentIds);
	}

	private SearchParameter aSearchParameter(String id) {
		SearchParameter retVal = new SearchParameter();
		retVal.setId(id);
		retVal.setCode("patient");
		retVal.addBase("AllergyIntolerance");
		retVal.setStatus(Enumerations.PublicationStatus.DRAFT);
		retVal.setType(Enumerations.SearchParamType.REFERENCE);
		retVal.setExpression("AllergyIntolerance.patient.where(resolve() is Patient)");

		return retVal;
	}

}
