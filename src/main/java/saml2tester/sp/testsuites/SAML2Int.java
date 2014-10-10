package saml2tester.sp.testsuites;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.NameID;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.SubjectConfirmation;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml2.metadata.SingleSignOnService;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.XMLObjectBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import saml2tester.common.SAMLAttribute;
import saml2tester.common.SAMLUtil;
import saml2tester.common.TestStatus;
import saml2tester.common.standardNames.MD;
import saml2tester.common.standardNames.SAML;
import saml2tester.common.standardNames.SAMLP;
import saml2tester.common.standardNames.SAMLmisc;
import saml2tester.sp.LoginAttempt;
import saml2tester.sp.SPConfiguration;
import saml2tester.sp.SPTestRunner;


public class SAML2Int extends TestSuite {
	/**
	 * Logger for this class
	 */
	private final Logger logger = LoggerFactory.getLogger(SPTestRunner.class);
	
	@Override
	public String getMockIdPProtocol() {
		return "http";
	}

	@Override
	public String getMockIdPHostname() {
		return "localhost";
	}

	@Override
	public int getMockIdPPort() {
		return 8080;
	}

	@Override
	public String getMockIdPSsoPath() {
		return "/sso";
	}

	@Override
	public String getmockIdPEntityID() {
		return "http://localhost:8080/sso";
	}

	@Override
	public String getIdPMetadata() {
		try {
			DefaultBootstrap.bootstrap();
		} catch (ConfigurationException e) {
			logger.error("Could not bootstrap OpenSAML", e);
		}
		XMLObjectBuilderFactory xmlbuilderfac = Configuration.getBuilderFactory();		
		EntityDescriptor ed = (EntityDescriptor) xmlbuilderfac.getBuilder(EntityDescriptor.DEFAULT_ELEMENT_NAME).buildObject(EntityDescriptor.DEFAULT_ELEMENT_NAME);
		IDPSSODescriptor idpssod = (IDPSSODescriptor) xmlbuilderfac.getBuilder(IDPSSODescriptor.DEFAULT_ELEMENT_NAME).buildObject(IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
		SingleSignOnService ssos = (SingleSignOnService) xmlbuilderfac.getBuilder(SingleSignOnService.DEFAULT_ELEMENT_NAME).buildObject(SingleSignOnService.DEFAULT_ELEMENT_NAME);
		
		ssos.setBinding(SAMLmisc.BINDING_HTTP_REDIRECT);
		ssos.setLocation(getMockIdPURL());
		
		idpssod.addSupportedProtocol(SAMLmisc.SAML20_PROTOCOL);
		idpssod.getSingleSignOnServices().add(ssos);
		
		ed.setEntityID(getmockIdPEntityID());
		ed.getRoleDescriptors().add(idpssod);
		
		// return the metadata as a string
		return SAMLUtil.toXML(ed);
	}

	/**
	 *  Tests the following part of the SAML2Int Profile:
	 *  	Identity Providers and Service Providers MUST provide a SAML 2.0 Metadata document representing its entity. 
	 *  	How metadata is exchanged is out of scope of this specification.
	 *   
	 * @author RiaasM
	 *
	 */
	public class MetadataAvailable implements MetadataTestCase {
		private String failedMessage;
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata is available (MUST requirement)";
		}

		@Override
		public String getSuccessMessage() {
			return "The Service Provider's metadata is available";
		}

		@Override
		public String getFailedMessage() {
			return failedMessage;
		}

		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata != null){
				NodeList mdEDs = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.ENTITYDESCRIPTOR);
				// there should be only one entity descriptor
				if(mdEDs.getLength() > 1){
					failedMessage = "The provided metadata contained metadata for multiple SAML entities";
					return TestStatus.CRITICAL;
				}
				else if(mdEDs.getLength() == 0){
					failedMessage = "The provided metadata contained no metadata for a SAML entity";
					return TestStatus.CRITICAL;
				}
				Node mdED = mdEDs.item(0);
				String curNS = mdED.getNamespaceURI();
				// check if the provided document is indeed SAML Metadata (or at least uses the SAML Metadata namespace)
				if(curNS != null && curNS.equalsIgnoreCase(MD.NAMESPACE)){
					return TestStatus.OK;
				}
				else{
					failedMessage = "The Service Provider's metadata did not use the SAML Metadata namespace";
					return TestStatus.ERROR;
				}
			}
			else{
				failedMessage = "The Service Provider's metadata was not available";
				return TestStatus.ERROR;
			}
		}
		
	}

	/**
	 *  Tests the following part of the SAML2Int Profile:
	 *  	Metadata documents provided by a Service Provider MUST include an <md:SPSSODescriptor> element containing 
	 *  	all necessary <md:KeyDescriptor> and <md:AssertionConsumerService> elements.
	 * @author RiaasM
	 *
	 */
	public class MetadataElementsAvailable implements MetadataTestCase{
		private String failedMessage;
	
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata contains all minimally required elements (MUST requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider's metadata contains all minimally required elements";
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		/**
		 * Check that the metadata contains at least one SPSSODescriptor containing at least one KeyDescriptor and 
		 * at least one AssertionConsumerService element.
		 */
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if (metadata != null){
				NodeList spssodList = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.SPSSODESCRIPTOR);
				
				// make sure you have at least one SPSSODescriptor
				if(spssodList.getLength() > 0){
					// go through all tags to check if they contain the required KeyDescriptor and AssertionConsumerService elements
					for (int i = 0 ; i < spssodList.getLength() ; i++){
						Node spssod = spssodList.item(i);
						// the elements must both be children of this node
						NodeList children = spssod.getChildNodes();
						
						// check all child nodes for the elements we need
						boolean kdFound = false;
						boolean acsFound = false;
						for (int j = 0 ; j < children.getLength() ; j++){
							Node curNode = children.item(j);
							if (curNode.getLocalName().equalsIgnoreCase(MD.KEYDESCRIPTOR)){
								kdFound = true;
							}
							if (curNode.getLocalName().equalsIgnoreCase(MD.ASSERTIONCONSUMERSERVICE)){
								acsFound = true;
							}
						}
						// check if both elements were found
						if (kdFound && acsFound){
							return TestStatus.OK;
						}
					}
					failedMessage = "None of the SPSSODescriptor elements in the Service Provider's metadata contained both the KeyDescriptor and the AssertionConsumerService element";
					return TestStatus.ERROR;
				}
				else{
					failedMessage = "The Service Provider's metadata did not contain an SPSSODescriptor";
					return TestStatus.ERROR;
				}
			}
			else {
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
		}
	}

	/**
	 * Tests the following part of the SAML2Int Profile: 
	 * 		The <saml2p:AuthnRequest> message issued by a Service Provider MUST be communicated to the Identity Provider 
	 * 		using the HTTP-REDIRECT binding [SAML2Bind].
	 * 
	 * @author RiaasM
	 *
	 */
	public class RequestByRedirect implements RequestTestCase{
		private String failedMessage; 

		@Override
		public String getDescription() {
			return "Test if the Service Provider can send its Authentication Requests using the HTTP-Redirect binding (MUST requirement)";
		}

		@Override
		public String getSuccessMessage() {
			return "The Service Provider sent its Authentication Request using the HTTP-Redirect binding";
		}

		@Override
		public String getFailedMessage() {
			return failedMessage;
		}

		@Override
		public TestStatus checkRequest(String request, String binding) {
			if (binding.equalsIgnoreCase(SAMLmisc.BINDING_HTTP_REDIRECT)){
				return TestStatus.OK;
			}
			else {
				failedMessage = "The Service Provider did not send its Authentication request using the HTTP-Redirect Binding. Instead, it used: "+binding;
				return TestStatus.ERROR;
			}
		}
		
	}
	
	/**
	 * Tests the following part of the SAML2Int Profile: 
	 * 		The <saml2p:AuthnRequest> message issued by a Service Provider MUST contain an AssertionConsumerServiceURL 
	 * 		attribute identifying the desired response location.
	 * @author RiaasM
	 *
	 */
	public class RequestContainsACSURL implements RequestTestCase{
		private String failedMessage; 

		@Override
		public String getDescription() {
			return "Test if the Service Provider's Authentication Request contains an AssertionConsumerServiceURL attribute (MUST requirement)";
		}

		@Override
		public String getSuccessMessage() {
			return "The Service Provider's Authentication Request contains an AssertionConsumerServiceURL attribute";
		}

		@Override
		public String getFailedMessage() {
			return failedMessage;
		}

		@Override
		public TestStatus checkRequest(String request, String binding) {
			Node acsURL = SAMLUtil.fromXML(request).getDocumentElement().getAttributes().getNamedItem(SAMLP.ASSERTIONCONSUMERSERVICEURL);
			if (acsURL != null){
				return TestStatus.OK;
			}
			else{
				failedMessage = "The Service Provider's Authentication Request did not contain an AssertionConsumerServiceURL attribute";
				return TestStatus.ERROR;
			}
		}
	}

	/**
	 * Tests the following part of the SAML2Int Profile: 
	 * 		The ProtocolBinding attribute, if present, MUST be set to urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST.
	 * @author RiaasM
	 *
	 */
	public class RequestProtocolBinding implements RequestTestCase{
		private String successMessage;
		private String failedMessage; 
	
		@Override
		public String getDescription() {
			return "Test if the Service Provider's Authentication Request contains a ProtocolBinding attribute set to HTTP POST (MUST requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return successMessage;
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkRequest(String request, String binding) {
			Node protBind = SAMLUtil.fromXML(request).getDocumentElement().getAttributes().getNamedItem(SAMLP.PROTOCOLBINDING);
			if (protBind == null){
				successMessage = "The Service Provider's Authentication Request does not contain a ProtocolBinding attribute";
				return TestStatus.OK;
			}
			else{
				if (protBind.getNodeValue().equals(SAMLmisc.BINDING_HTTP_POST)){
					successMessage = "The Service Provider's Authentication Request contained a ProtocolBinding attribute set to HTTP POST";
					return TestStatus.OK;
				}
				else{
					// be more specific in the failed test's message, so it's easier to know what went wrong
					failedMessage = "The Service Provider's Authentication Request contained a ProtocolBinding attribute that was not set to '"+SAMLmisc.BINDING_HTTP_POST+"'";
					return TestStatus.ERROR;
				}
			}
		}
	}

	/**
	 * Tests the following part of the SAML2Int Profile: 
	 * 		The <saml2p:AuthnRequest> message MUST NOT contain a <saml2:Subject> element.
	 * @author RiaasM
	 *
	 */
	public class RequestNoSubject implements RequestTestCase{	
		@Override
		public String getDescription() {
			return "Test if the Service Provider's Authentication Request contains no Subject node (MUST requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider's Authentication Request contains no Subject node";
		}
	
		@Override
		public String getFailedMessage() {
			return "The Service Provider's Authentication Request contained a Subject node";
		}
	
		@Override
		public TestStatus checkRequest(String request, String binding) {
			NodeList subjects = SAMLUtil.fromXML(request).getElementsByTagNameNS(SAML.NAMESPACE, SAML.SUBJECT);
			if (subjects.getLength() == 0){
				return TestStatus.OK;
			}
			else{
				return TestStatus.ERROR;
			}
		}
	}

	/**
	 * Tests the following part of the following part of the SAML2Int Profile: 
	 * Service Providers, if they rely at all on particular name identifier formats, MUST support one of the following:
	 * 		urn:oasis:names:tc:SAML:2.0:nameid-format:persistent
	 * 		urn:oasis:names:tc:SAML:2.0:nameid-format:transient
	 * 
	 * @author RiaasM
	 */
	public class LoginTransientPersistent implements LoginTestCase{
		private String failedMessage;
		private String successMessage;

		@Override
		public String getDescription() {
			return "Test if the Service Provider allows logging in with either the persistent or transient name identifier format (MUST requirement)";
		}

		@Override
		public String getSuccessMessage() {
			return successMessage;
		}

		@Override
		public String getFailedMessage() {
			return failedMessage;
		}

		@Override
		public List<LoginAttempt> getLoginAttempts() {
			ArrayList<LoginAttempt> attempts = new ArrayList<LoginAttempt>();
		
			// create the classes that will contain the login attempts and SAML Responses
			class LoginAttemptTransient implements LoginAttempt{

				@Override
				public boolean isSPInitiated() {
					return true;
				}
				
				@Override
				public String getResponse(String request) {
					// retrieve the request ID from the request
					String requestID = SAMLUtil.getSamlMessageID(request);
					
					// create the minimally required Response
					Response responseTransient = createMinimalWebSSOResponse();
					// add attributes and sign the assertions in the response
					List<Assertion> assertions = responseTransient.getAssertions();
					for (Assertion assertion : assertions){
						// create nameid with transient format
						NameID nameid = (NameID) Configuration.getBuilderFactory().getBuilder(NameID.DEFAULT_ELEMENT_NAME).buildObject(NameID.DEFAULT_ELEMENT_NAME);
						nameid.setValue("_"+UUID.randomUUID().toString());
						nameid.setFormat(SAMLmisc.NAMEID_FORMAT_TRANSIENT);
						assertion.getSubject().setNameID(nameid);

						// set the InReplyTo attribute on the subjectconfirmationdata of all subjectconfirmations
						List<SubjectConfirmation> subconfs = assertion.getSubject().getSubjectConfirmations();
						for (SubjectConfirmation subconf : subconfs){
							subconf.getSubjectConfirmationData().setInResponseTo(requestID);
						}
						// add the attributes
						addTargetSPAttributes(assertion);
						SAMLUtil.sign(assertion, getIdPPrivateKey(null), getIdPCertificate(null));
					}
					// add the InReplyTo attribute to the Response as well
					responseTransient.setInResponseTo(requestID);

					return SAMLUtil.toXML(responseTransient);
				}
			}
			
			class LoginAttemptPersistent implements LoginAttempt{

				@Override
				public boolean isSPInitiated() {
					return true;
				}
				
				@Override
				public String getResponse(String request) {
					// retrieve the request ID from the request
					String requestID = SAMLUtil.getSamlMessageID(request);
					
					// create the minimally required Response
					Response responsePersistent = createMinimalWebSSOResponse();
					// add attributes and sign the assertions in the response
					List<Assertion> assertions = responsePersistent.getAssertions();
					for (Assertion assertion : assertions){
						// create nameid with persistent format
						NameID nameid = (NameID) Configuration.getBuilderFactory().getBuilder(NameID.DEFAULT_ELEMENT_NAME).buildObject(NameID.DEFAULT_ELEMENT_NAME);
						nameid.setValue("_"+UUID.randomUUID().toString());
						nameid.setFormat(SAMLmisc.NAMEID_FORMAT_PERSISTENT);
						assertion.getSubject().setNameID(nameid);

						// set the InReplyTo attribute on the subjectconfirmationdata of all subjectconfirmations
						List<SubjectConfirmation> subconfs = assertion.getSubject().getSubjectConfirmations();
						for (SubjectConfirmation subconf : subconfs){
							subconf.getSubjectConfirmationData().setInResponseTo(requestID);
						}
						// add the attributes
						addTargetSPAttributes(assertion);
						SAMLUtil.sign(assertion, getIdPPrivateKey(null), getIdPCertificate(null));
					}
					// add the InReplyTo attribute to the Response as well
					responsePersistent.setInResponseTo(requestID);

					return SAMLUtil.toXML(responsePersistent);
				}
			}
			attempts.add(new LoginAttemptTransient());
			attempts.add(new LoginAttemptPersistent());
			return attempts;
		}

		@Override
		public TestStatus checkLoginResults(List<Boolean> loginResults) {
			// the results should come back in the same order as they were provided, so we can check which login attempts succeeded
			if (loginResults.get(0).booleanValue()){	
				if (loginResults.get(1).booleanValue()){
					successMessage = "The Service Provider could log in with both transient and persistent name identifier format";
					return TestStatus.OK;
				}
				else{
					successMessage = "The Service Provider could log in with transient name identifier format";
					return TestStatus.OK;
				}
			}
			else{
				if (loginResults.get(1).booleanValue()){
					successMessage = "The Service Provider could log in with persistent name identifier format";
					return TestStatus.OK;
				}
				else{
					failedMessage = "The Service Provider could not log in with either transient or persistent name identifier format";
					return TestStatus.ERROR;
				}
			}
		}
	}
	
	/**
	 * Tests the following part of the following part of the SAML2Int Profile: 
	 * Service Providers MUST support unsolicited <saml2p:Response> messages (i.e., responses that are not the result of an 
	 * earlier <saml2p:AuthnRequest> message).
	 * 
	 * @author RiaasM
	 */
	public class LoginIdPInitiated implements LoginTestCase{

		@Override
		public String getDescription() {
			return "Test if the Service Provider allows IdP-initiated login (MUST requirement)";
		}

		@Override
		public String getSuccessMessage() {
			return "The Service Provider allowed IdP-initiated login";
		}

		@Override
		public String getFailedMessage() {
			return "The Service Provider did not allow IdP-initiated login";
		}

		@Override
		public List<LoginAttempt> getLoginAttempts() {
			ArrayList<LoginAttempt> attempts = new ArrayList<LoginAttempt>();
		
			// create the classes that will contain the login attempts and SAML Responses
			class LoginAttemptIdPInitiated implements LoginAttempt{

				@Override
				public boolean isSPInitiated() {
					return false;
				}
				
				@Override
				public String getResponse(String request) {
					// create the minimally required Response
					Response response = createMinimalWebSSOResponse();
					// add attributes and sign the assertions in the response
					List<Assertion> assertions = response.getAssertions();
					for (Assertion assertion : assertions){
						// create nameid with transient format
						NameID nameid = (NameID) Configuration.getBuilderFactory().getBuilder(NameID.DEFAULT_ELEMENT_NAME).buildObject(NameID.DEFAULT_ELEMENT_NAME);
						nameid.setValue("_"+UUID.randomUUID().toString());
						nameid.setFormat(SAMLmisc.NAMEID_FORMAT_TRANSIENT);
						assertion.getSubject().setNameID(nameid);

						// add the attributes
						addTargetSPAttributes(assertion);
						SAMLUtil.sign(assertion, getIdPPrivateKey(null), getIdPCertificate(null));
					}
					// add the InReplyTo attribute to the Response as well

					String responseXML = SAMLUtil.toXML(response);
					logger.trace(responseXML);
					return responseXML;
				}
			}
			
			attempts.add(new LoginAttemptIdPInitiated());
			return attempts;
		}

		@Override
		public TestStatus checkLoginResults(List<Boolean> loginResults) {
			// the results should come back in the same order as they were provided, so we can check which login attempts succeeded
			if (loginResults.get(0).booleanValue())	
				return TestStatus.OK;
			else
				return TestStatus.ERROR;
		}
	}
	
	/**
	 * Tests the following part of the following part of the SAML2Int Profile:
	 * 		Any <saml2:Attribute> elements exchanged via any SAML 2.0 messages, assertions, [...] MUST contain 
	 * 		a NameFormat of urn:oasis:names:tc:SAML:2.0:attrname-format:uri.
	 * 
	 * @author RiaasM
	 *
	 */
	public class ConfigAttrNameFormat implements ConfigTestCase{
		private String successMessage;
		private String failedMessage;
	
		@Override
		public String getDescription() {
			return "Test if the correct NameFormat is configured for attributes (MUST requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return successMessage;
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkConfig(SPConfiguration config) {
			ArrayList<SAMLAttribute> attrs = config.getAttributes();
			if (attrs.size() == 0){
				successMessage = "No attributes were configured so the NameFormat restriction doesn't apply";
				return TestStatus.OK;
			}
			else{
				// make sure all attributes use the correct NameFormat
				for (SAMLAttribute attr : attrs){
					if(!attr.getNameFormat().equals(SAMLmisc.NAMEFORMAT_URI)){
						// be more specific in the failed test's message, so it's easier to know what went wrong
						failedMessage = "A configured attribute uses a NameFormat other than '"+SAMLmisc.NAMEFORMAT_URI+"'";
						return TestStatus.WARNING;
					}
				}
				successMessage = "All attributes were configured with the correct NameFormat";
				return TestStatus.OK;
			}
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile:
	 *  	Entities SHOULD publish its metadata using the Well-Known Location method defined in [SAML2Meta].
	 * This means that the metadata should be available on a URL that is represented by the Entity ID
	 * @author RiaasM
	 *
	 */
	public class MetadataWellKnownLocation implements MetadataTestCase {
		private String failedMessage = "The metadata was not found at the Well-Known Location (the URL represented by the Entity ID)";
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata is available at the Well-Known Location (SHOULD requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider's metadata is available at the Well-Known Location";
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata != null){
				NodeList mdEDs = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.ENTITYDESCRIPTOR);
				// there should be only one entity descriptor
				if(mdEDs.getLength() > 1){
					failedMessage = "The provided metadata contained metadata for multiple SAML entities";
					return TestStatus.CRITICAL;
				}
				else if(mdEDs.getLength() == 0){
					failedMessage = "The provided metadata contained no metadata for a SAML entity";
					return TestStatus.CRITICAL;
				}
				Node mdED = mdEDs.item(0);
				String entityID = mdED.getAttributes().getNamedItem(MD.ENTITYID).getNodeValue();
				// try to access the URL represented by the Entity ID and try to retrieve the metadata XML from it
				try{
					DocumentBuilderFactory docBuilderFac = DocumentBuilderFactory.newInstance();
					docBuilderFac.setNamespaceAware(true);
					docBuilderFac.setValidating(false);
					Document mdFromURL = docBuilderFac.newDocumentBuilder().parse(entityID);
					// normalize both XML documents before comparison
					metadata.normalizeDocument();
					mdFromURL.normalizeDocument();
					// check if the document is actually XML
					if(mdFromURL.getXmlVersion() == null){
						return TestStatus.WARNING;
					}
					// chec if the retrieved XML document is the same as the provided metadata
					else if (mdFromURL.isEqualNode(metadata)){
						return TestStatus.OK;
					}
					else{
						return TestStatus.WARNING;
					}
				}
				catch(MalformedURLException malf){
					return TestStatus.WARNING;
				} catch (ParserConfigurationException e) {
					return TestStatus.WARNING;
				} catch (SAXException e) {
					return TestStatus.WARNING;
				} catch (IOException e) {
					return TestStatus.WARNING;
				}
			}
			else {
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile:
	 *  	The metadata SHOULD also include one or more <md:NameIDFormat> elements indicating which <saml2:NameID> 
	 *  	Format values are supported 
	 * 
	 * @author RiaasM
	 *
	 */
	public class MetadataNameIDFormat implements MetadataTestCase {
		private String failedMessage = "The Service Provider's metadata does not contain a NameIDFormat element";
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata contains at least one NameIDFormat element (SHOULD requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider's metadata contains a NameIDFormat element";
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata != null){
				NodeList nameidformats = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.NAMEIDFORMAT);
				// check if there is at least one NameIDFormat
				if(nameidformats.getLength() > 0){
					return TestStatus.OK;
				}
				else {
					return TestStatus.WARNING;
				}
			}
			else {
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile:
	 *  	Any <saml2:Attribute> elements exchanged via any SAML 2.0 [...] metadata MUST contain 
	 * 		a NameFormat of urn:oasis:names:tc:SAML:2.0:attrname-format:uri.
	 * 
	 * @author RiaasM
	 *
	 */
	public class MetadataAttrNameFormatURI implements MetadataTestCase {
		private String successMessage = "The Service Provider's metadata contains only attributes with NameFormat value of '"+SAMLmisc.NAMEFORMAT_URI+"'";
		private String failedMessage = "The Service Provider's metadata contains attributes with a NameFormat value other than '"+SAMLmisc.NAMEFORMAT_URI+"'";
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata contains only one attributes with NameFormat value of '"+SAMLmisc.NAMEFORMAT_URI+"' (MUST requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return successMessage;
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata == null){
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
			
			NodeList attrs = metadata.getElementsByTagNameNS(MD.NAMESPACE, SAML.ATTRIBUTE);
			
			if (attrs.getLength() == 0){
				successMessage = "The Service Provider's metadata contains no attributes, so the requirement does not apply";
				return TestStatus.OK;
			}

			// make sure all attributes use the correct NameFormat
			for (int i = 0; i < attrs.getLength(); i++){
				NamedNodeMap attr = attrs.item(i).getAttributes();
				Node nameformat = attr.getNamedItem(SAML.NAMEFORMAT);
					
				// check if the nameformat value is URI
				if(nameformat == null || !nameformat.getNodeValue().equals(SAMLmisc.NAMEFORMAT_URI)){
					// be more specific in the failed test's message, so it's easier to know what went wrong
					failedMessage = "The Service Provider's metadata contain an attribute with a NameFormat value other than '"+SAMLmisc.NAMEFORMAT_URI+"'";
					return TestStatus.WARNING;
				}
			}
			successMessage = "All attributes were configured with the correct NameFormat";
			return TestStatus.OK;
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile:
	 *  	The metadata SHOULD also include [...] and one or more <md:AttributeConsumingService> elements describing 
	 *  	the service(s) offered and their attribute requirements.
	 * This means that the metadata should be available on a URL that is represented by the Entity ID
	 * @author RiaasM
	 *
	 */
	public class MetadataAttrConsumingService implements MetadataTestCase {
		private String failedMessage = "The Service Provider's metadata does not contain a AttributeConsumingService element";
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata contains at least one AttributeConsumingService element (SHOULD requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider's metadata contains a AttributeConsumingService element";
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata != null){
				NodeList attrConsServs = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.ATTRIBUTECONSUMINGSERVICE);
				// check if there is at least one AttributeConsumingService
				if(attrConsServs.getLength() > 1){
					return TestStatus.OK;
				}
				else {
					return TestStatus.WARNING;
				}
			}
			else {
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile:
	 *  	Metadata provided by Service Provider SHOULD also contain a descriptive name of the service that the 
	 *  	Service Provider represents (not the company) [...] The name 
	 *  	should be placed in the <md:ServiceName> in the <md:AttributeConsumingService> container.
	 * 
	 * @author RiaasM
	 *
	 */
	public class MetadataServiceNameAvailable implements MetadataTestCase {
		private String failedMessage;
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata contains at least one ServiceName element (SHOULD requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider's metadata contains at least one ServiceName element";
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata != null){
				NodeList servNames = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.SERVICENAME);
				// check if there is at least one ServiceName
				if(servNames.getLength() > 1){
					return TestStatus.OK;
				}
				else {
					failedMessage = "The Service Provider's metadata does not contain any ServiceName elements";
					return TestStatus.WARNING;
				}
			}
			else {
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile:
	 *  	Metadata provided by Service Provider SHOULD also contain a descriptive name of the service that the 
	 *  	Service Provider represents (not the company) [...] The name 
	 *  	should be placed in the <md:ServiceName> in the <md:AttributeConsumingService> container.
	 * 
	 * @author RiaasM
	 *
	 */
	public class MetadataServiceNameEnglish implements MetadataTestCase {
		private String failedMessage;
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata contains at least one ServiceName with language set to English (SHOULD requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider's metadata contains at least one English ServiceName with language set to English";
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata != null){
				NodeList servNames = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.SERVICENAME);
				// check if there is at least one AttributeConsumingService
				if(servNames.getLength() > 1){
					// check for service name element in each AttributeConsumingService
					for (int i = 0; i < servNames.getLength(); i++){
						Node servName = servNames.item(i);
						String lang = servName.getAttributes().getNamedItemNS(MD.NAMESPACE_XML, MD.LANG).getNodeValue();
						if (lang.contains(SAMLmisc.LANG_ENGLISH)){
							return TestStatus.OK;
						}
					}
					failedMessage = "The Service Provider's metadata does not contain any ServiceName elements with language set to English";
					return TestStatus.WARNING;
				}
				else {
					failedMessage = "The Service Provider's metadata does not contain any ServiceName elements";
					return TestStatus.WARNING;
				}
			}
			else {
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile:
	 *  	If a Service Provider forgoes the use of TLS/SSL for its Assertion Consumer Service endpoints, then [...]
	 *  	Note that use of TLS/SSL is RECOMMENDED.
	 * 
	 * @author RiaasM
	 *
	 */
	public class MetadataHTTPS implements MetadataTestCase {
		private String failedMessage;
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider uses TLS/SSL for its Assertion Consumer Service endpoints (RECOMMENDATION)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider uses TLS/SSL for all its Assertion Consumer Service endpoints";
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata != null){
				NodeList ACSs = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.ASSERTIONCONSUMERSERVICE);
				// check if there is at least one ACS
				if(ACSs.getLength() > 0){
					// check for each ACS if they are using TLS/SSL
					int HTTPScount = 0;
					for (int i = 0; i < ACSs.getLength(); i++){
						Node ACS = ACSs.item(i);
						String ACSLoc = ACS.getAttributes().getNamedItem(MD.LOCATION).getNodeValue();
						try {
							URL ACSLocURL = new URL(ACSLoc);
							if (ACSLocURL.getProtocol().equalsIgnoreCase("https")){
								HTTPScount++;
							}
						} catch (MalformedURLException e) {
							failedMessage = "The Service Provider's metadata contains at least one malformed Assertion Consumer Service Locations URL";
							return TestStatus.CRITICAL;
						}
					}
					if (HTTPScount == 0){
						failedMessage = "The Service Provider neglects using TLS/SSL on all of its Assertion Consumer Service endpoints";
						return TestStatus.WARNING;
					}
					else if (HTTPScount < ACSs.getLength()){
						failedMessage = "The Service Provider neglect using TLS/SSL on some of its Assertion Consumer Service endpoints";
						return TestStatus.WARNING;
					}
					else if (HTTPScount == ACSs.getLength()){
						return TestStatus.OK;
					}
					else{
						// HTTPScount is larger than the the length of the ACSs Nodelist, which should never be possible
						failedMessage = "Error occurred in the MetadataHTTPS test case while checking the ACS URLs";
						return TestStatus.CRITICAL;
					}
					
				}
				else {
					failedMessage = "The Service Provider's metadata does not contain any Assertion Consumer Service elements";
					return TestStatus.WARNING;
				}
			}
			else {
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile:
	 *  	If a Service Provider forgoes the use of TLS/SSL for its Assertion Consumer Service endpoints, 
	 *  	then its metadata SHOULD include a <md:KeyDescriptor> suitable for XML Encryption. 
	 * 
	 * @author RiaasM
	 *
	 */
	public class MetadataEncryptionKey implements MetadataTestCase {
		private String failedMessage;
		private String successMessage;
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata contains an encryption key when not using TLS/SSL for its Assertion Consumer Service endpoints (SHOULD requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return successMessage;
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata != null){
				NodeList ACSs = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.ASSERTIONCONSUMERSERVICE);
				// check if there is at least one ACS
				if(ACSs.getLength() > 0){
					// check for each ACS if they are using TLS/SSL
					int HTTPScount = 0;
					for (int i = 0; i < ACSs.getLength(); i++){
						Node ACS = ACSs.item(i);
						String ACSLoc = ACS.getAttributes().getNamedItem(MD.LOCATION).getNodeValue();
						try {
							URL ACSLocURL = new URL(ACSLoc);
							if (ACSLocURL.getProtocol().equalsIgnoreCase("https")){
								HTTPScount++;
							}
						} catch (MalformedURLException e) {
							failedMessage = "The Service Provider's metadata contains at least one malformed Assertion Consumer Service Locations URL";
							return TestStatus.CRITICAL;
						}
					}
					// check if all ACSs are using TLS/SSL
					if (HTTPScount < ACSs.getLength()){
						// check if at least one encryption key is available
						NodeList KDs = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.KEYDESCRIPTOR);
						if(KDs.getLength() > 0){
							for (int i = 0; i < KDs.getLength(); i++){
								Node KD = KDs.item(i);
								NamedNodeMap KDattr = KD.getAttributes();
								if (KDattr == null){
									// no attributes found, so no "use" attribute found
									// without use attribute, key is used for both signing and encryption,
									// so we have found an encryption key
									successMessage = "The Service Provider's metadata contains an encryption key";
									return TestStatus.OK;
								}
								else {
									Node KDuse = KDattr.getNamedItem(MD.USE);
									if (KDuse == null){
										// value should only be "signing" or "encryption" so metadata is invalid
										failedMessage = "The Service Provider's metadata contains an empty 'use' attribute, which makes the metadata invalid";
										return TestStatus.CRITICAL;
									}
									else{
										String use = KDuse.getNodeValue();
										if (use.isEmpty()){
											// value should only be "signing" or "encryption" so metadata is invalid
											failedMessage = "The Service Provider's metadata contains an empty 'use' attribute, which makes the metadata invalid";
											return TestStatus.CRITICAL;
										}
										else if (use.equals(MD.KEYTYPE_ENCRYPTION)){
											successMessage = "The Service Provider's metadata contains an encryption key";
											return TestStatus.OK;
										}
									}
								}
							}
							failedMessage = "The Service Provider's metadata does not contain an encryption key and neglects to use TLS/SSL for all of its Assertion Consumer Service endpoints";
							return TestStatus.WARNING;
						}
						else{
							failedMessage = "The Service Provider's metadata does not contain any keys and neglects to use TLS/SSL for all of its Assertion Consumer Service endpoints";
							return TestStatus.WARNING;
						}
					}
					else if (HTTPScount == ACSs.getLength()){
						successMessage = "The Service Provider uses TLS/SSL on all of its Assertion Consumer Service endpoints, so this requirement does not apply";
						return TestStatus.OK;
					}
					else{
						// HTTPScount is larger than the the length of the ACSs Nodelist, which should never be possible
						failedMessage = "Error occurred in the MetadataHTTPS test case while checking the ACS URLs";
						return TestStatus.CRITICAL;
					}
				}
				else {
					failedMessage = "The Service Provider's metadata does not contain any Assertion Consumer Service elements";
					return TestStatus.WARNING;
				}
			}
			else {
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile: 
	 * 		Metadata provided by
	 * 		both Identity Providers and Service Provider SHOULD contain contact
	 * 		information for support and for a technical contact. The
	 * 		<md:EntityDescriptor> element SHOULD contain both a <md:ContactPerson>
	 * 		element with a contactType of "support" and a <md:ContactPerson> element
	 * 		with a contactType of "technical".
	 * 
	 * @author LaurentB, RiaasM
	 * 
	 */
	public class MetadataContactInfo implements MetadataTestCase {
		private String failedMessage;
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata contains contact information for a support and a technical contact (SHOULD requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider's metadata contains contact information for both a support and a technical contact";
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata == null){
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
			
			NodeList contactPersons = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.CONTACTPERSON);
			
			// check if there is not none contact persons
			if(contactPersons.getLength() == 0){
				failedMessage = "The Service Provider's metadata contains no Contact Persons";
				return TestStatus.WARNING;
			}
			
			// check if there is not one contact persons
			if(contactPersons.getLength() == 1){
				failedMessage = "The Service Provider's metadata contains only one Contact Person";
				return TestStatus.WARNING;
			}
			
			// check if there is at least one support and one technical contact person
			boolean supportFound = false;
			boolean technicalFound = false;
			for (int i = 0; i < contactPersons.getLength(); i++){
				Node contactPerson = contactPersons.item(i);
				String contactType = contactPerson.getAttributes().getNamedItem(MD.CONTACTTYPE).getNodeValue();
				if (contactType.equals(MD.CONTACTTYPE_SUPPORT)) {
					supportFound = true;
				}
				else if (contactType.equals(MD.CONTACTTYPE_TECHNICAL)){
					technicalFound = true;
				}
			}
			
			if (supportFound && technicalFound){
				return TestStatus.OK;
			}
			else if (supportFound){
				failedMessage = "The Service Provider's metadata contains only support Contact Persons";
				return TestStatus.WARNING;
			}
			else if (technicalFound){
				failedMessage = "The Service Provider's metadata contains only technical Contact Persons";
				return TestStatus.WARNING;
			}
			else {
				failedMessage = "The Service Provider's metadata contains no support or technical Contact Persons";
				return TestStatus.WARNING;
			}
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile: 
	 * 		The <md:ContactPerson> elements SHOULD contain at least one <md:EmailAddress>. 
	 * 
	 * @author RiaasM
	 * 
	 */
	public class MetadataContactEmail implements MetadataTestCase {
		private String failedMessage;
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata contains EmailAddress elements for all its ContactPerson elements (SHOULD requirement)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider's metadata contains EmailAddress elements for all its ContactPerson elements";
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata == null){
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
			
			NodeList contactPersons = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.CONTACTPERSON);
			
			// check if there are contactpersons found
			if(contactPersons.getLength() == 0){
				failedMessage = "The Service Provider's metadata contains no Contact Persons";
				return TestStatus.WARNING;
			}
			
			// check if each contactperson has at least one emailaddress
			int emailCount = 0;
			for (int i = 0; i < contactPersons.getLength(); i++){
				Node contactPerson = contactPersons.item(i);
				NodeList emailaddresses = contactPerson.getChildNodes();
				for (int j = 0; j < emailaddresses.getLength(); j++){
					if (emailaddresses.item(j).getNodeName().equals(MD.EMAILADDRESS)){
						// found an emailaddress element for this contactperson 
						emailCount++;
						break;
					}
				}
			}
			
			if (emailCount == 0){
				failedMessage = "The Service Provider's metadata contains no EmailAddress elements for any of its ContactPerson elements";
				return TestStatus.WARNING;
			}
			else if (emailCount < contactPersons.getLength()){
				failedMessage = "The Service Provider's metadata contains EmailAddress elements for some, but not all, of its ContactPerson elements";
				return TestStatus.WARNING;
			}
			else if (emailCount == contactPersons.getLength()){
				return TestStatus.OK;
			}
			else {
				// emailCount is larger than the the length of the contactPersons Nodelist, which should never be possible
				failedMessage = "Error occurred in the MetadataContactEmail test case while checking the ContactPerson elements";
				return TestStatus.CRITICAL;
			}
		}
		
	}

	/**
	 * Tests the following part of the SAML2Int Profile:
	 *  	Reliance on other formats by Service Providers is NOT RECOMMENDED.
	 *  This can only partially be tested, namely by checking what NameIDFormat is configured in the SP's metadata
	 * 
	 * @author RiaasM
	 *
	 */
	public class MetadataNameIDFormatOther implements MetadataTestCase {
		private String failedMessage;
		
		@Override
		public String getDescription() {
			return "Test if the Service Provider's metadata contains only NameIDFormat values of other than '"+SAMLmisc.NAMEID_FORMAT_TRANSIENT+"' or '"+SAMLmisc.NAMEID_FORMAT_PERSISTENT+"' (RECOMMENDATION)";
		}
	
		@Override
		public String getSuccessMessage() {
			return "The Service Provider's metadata contains only NameIDFormat values of other than '"+SAMLmisc.NAMEID_FORMAT_TRANSIENT+"' or '"+SAMLmisc.NAMEID_FORMAT_PERSISTENT+"' (RECOMMENDATION)";
		}
	
		@Override
		public String getFailedMessage() {
			return failedMessage;
		}
	
		@Override
		public TestStatus checkMetadata(Document metadata) {
			if(metadata == null){
				failedMessage = "The test case could not be performed because there was no metadata available";
				return TestStatus.CRITICAL;
			}
					
			NodeList nameidformats = metadata.getElementsByTagNameNS(MD.NAMESPACE, MD.NAMEIDFORMAT);
			
			// check if there is at least one NameIDFormat
			if(nameidformats.getLength() == 0){
				failedMessage = "The Service Provider's metadata does not contain a NameIDFormat element";
				return TestStatus.WARNING;
			}
			
			// check the value of all NameIDFormats
			for (int i = 0; i < nameidformats.getLength(); i++){
				String nameidformatValue = nameidformats.item(i).getTextContent();
				if (nameidformatValue == null){
					failedMessage = "The Service Provider's metadata contains an empty 'NameIDFormat' element, which makes the metadata invalid";
					return TestStatus.CRITICAL;
				}
				else if(!nameidformatValue.equals(SAMLmisc.NAMEID_FORMAT_TRANSIENT) && !nameidformatValue.equals(SAMLmisc.NAMEID_FORMAT_PERSISTENT)){
					// SP uses a NameIDFormat other than transient and persistent
					failedMessage = "The Service Provider's metadata contains at least one NameIDFormat value other than '"+SAMLmisc.NAMEID_FORMAT_TRANSIENT+"' or '"+SAMLmisc.NAMEID_FORMAT_PERSISTENT+"'";
					return TestStatus.WARNING;
				}
			}
			return TestStatus.OK;
		}	
	}
}
