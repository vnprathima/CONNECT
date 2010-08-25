package gov.hhs.fha.nhinc.docsubmission.nhin.deferred.request;

import javax.annotation.Resource;
import javax.jws.WebService;
import javax.xml.ws.BindingType;
import javax.xml.ws.WebServiceContext;

/**
 *
 * @author JHOPPESC
 */
@WebService(serviceName = "XDRDeferredRequest_Service", portName = "XDRDeferredRequest_Port_Soap", endpointInterface = "ihe.iti.xdr._2007.XDRDeferredRequestPortType", targetNamespace = "urn:ihe:iti:xdr:2007", wsdlLocation = "WEB-INF/wsdl/NhinXDRRequest/NhinXDRDeferredRequest.wsdl")
@BindingType(value = javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING)
public class NhinXDRRequest {
    @Resource
    private WebServiceContext context;

    public gov.hhs.healthit.nhin.XDRAcknowledgementType provideAndRegisterDocumentSetBDeferredRequest(ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType body) {
        return new NhinDocSubmissionDeferredRequestImpl().provideAndRegisterDocumentSetBRequest(body, context);
    }

}
