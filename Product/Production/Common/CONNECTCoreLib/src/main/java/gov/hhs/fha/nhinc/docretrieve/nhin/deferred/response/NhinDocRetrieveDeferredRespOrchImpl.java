/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *  
 * Copyright 2010(Year date of delivery) United States Government, as represented by the Secretary of Health and Human Services.  All rights reserved.
 *  
 */
package gov.hhs.fha.nhinc.docretrieve.nhin.deferred.response;

import gov.hhs.fha.nhinc.async.AsyncMessageProcessHelper;
import gov.hhs.fha.nhinc.asyncmsgs.dao.AsyncMsgRecordDao;
import gov.hhs.fha.nhinc.auditrepository.AuditRepositoryLogger;
import gov.hhs.fha.nhinc.common.auditlog.DocRetrieveResponseMessageType;
import gov.hhs.fha.nhinc.common.auditlog.LogEventRequestType;
import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.common.nhinccommon.HomeCommunityType;
import gov.hhs.fha.nhinc.common.nhinccommonentity.RespondingGatewayCrossGatewayRetrieveResponseType;
import gov.hhs.fha.nhinc.docretrieve.DocRetrieveDeferredAuditLogger;
import gov.hhs.fha.nhinc.docretrieve.adapter.deferred.response.proxy.AdapterDocRetrieveDeferredRespProxy;
import gov.hhs.fha.nhinc.docretrieve.adapter.deferred.response.proxy.AdapterDocRetrieveDeferredRespProxyObjectFactory;
import gov.hhs.fha.nhinc.docretrieve.nhin.deferred.NhinDocRetrieveDeferred;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants;
import gov.hhs.fha.nhinc.saml.extraction.SamlTokenExtractorHelper;
import gov.hhs.fha.nhinc.transform.document.DocRetrieveAckTranforms;
import gov.hhs.fha.nhinc.util.HomeCommunityMap;
import gov.hhs.healthit.nhin.DocRetrieveAcknowledgementType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Created by
 * User: ralph
 * Date: Aug 2, 2010
 * Time: 1:50:15 PM
 */
public class NhinDocRetrieveDeferredRespOrchImpl extends NhinDocRetrieveDeferred {

    private static Log log = LogFactory.getLog(NhinDocRetrieveDeferredRespOrchImpl.class);

    protected AsyncMessageProcessHelper createAsyncProcesser() {
        return new AsyncMessageProcessHelper();
    }

    /**
     *
     * @param body
     * @param assertion
     * @return deferred doc retrieve ack
     */
    public DocRetrieveAcknowledgementType sendToRespondingGateway(RetrieveDocumentSetResponseType body, AssertionType assertion) {
        log.debug("Begin - sendToRespondingGateway");

        DocRetrieveAcknowledgementType response = null;
        DocRetrieveDeferredAuditLogger auditLog = new DocRetrieveDeferredAuditLogger();
        String homeCommunityId = SamlTokenExtractorHelper.getHomeCommunityId();
        String requestCommunityId = HomeCommunityMap.getCommunitIdForDeferredRDResponse(body);

        auditLog.auditDocRetrieveDeferredResponse(body, NhincConstants.AUDIT_LOG_INBOUND_DIRECTION, NhincConstants.AUDIT_LOG_NHIN_INTERFACE, assertion, requestCommunityId);

        // ASYNCMSG PROCESSING - RSPRCVD
        AsyncMessageProcessHelper asyncProcess = createAsyncProcesser();

        RespondingGatewayCrossGatewayRetrieveResponseType nhinResponse = new RespondingGatewayCrossGatewayRetrieveResponseType();
        nhinResponse.setRetrieveDocumentSetResponse(body);
        nhinResponse.setAssertion(assertion);

        String messageId = "";
        if (assertion.getRelatesToList() != null && assertion.getRelatesToList().size() > 0) {
            messageId = assertion.getRelatesToList().get(0);
        }

        boolean bIsQueueOk = asyncProcess.processRetrieveDocumentsResponse(messageId, AsyncMsgRecordDao.QUEUE_STATUS_RSPRCVD, AsyncMsgRecordDao.QUEUE_STATUS_RSPRCVDERR, nhinResponse);

        if (isServiceEnabled(NhincConstants.NHINC_DOCUMENT_RETRIEVE_DEFERRED_RESPONSE_SERVICE_KEY)) {
            if (isInPassThroughMode(NhincConstants.NHINC_DOCUMENT_RETRIEVE_DEFERRED_RESPONSE_SERVICE_PASSTHRU_PROPERTY)) {
                response = sendDocRetrieveDeferredResponseToAgency(body, assertion, requestCommunityId);
            } else {
                response = serviceDocRetrieveInternal(body, assertion, homeCommunityId, requestCommunityId);
            }
        } else {
            // Service is not enabled so we are not doing anything with this response
            String ackMsg = "Document Retrieve Deferred Response Service Not Enabled";
            log.warn(ackMsg);

            // Set the error acknowledgement status
            response = DocRetrieveAckTranforms.createAckMessage(NhincConstants.DOC_RETRIEVE_DEFERRED_REQ_ACK_FAILURE_STATUS_MSG, NhincConstants.DOC_RETRIEVE_DEFERRED_ACK_ERROR_INVALID, ackMsg);
        }

        // ASYNCMSG PROCESSING - RSPRCVDACK
        bIsQueueOk = asyncProcess.processAck(messageId, AsyncMsgRecordDao.QUEUE_STATUS_RSPRCVDACK, AsyncMsgRecordDao.QUEUE_STATUS_RSPRCVDERR, response);

        auditLog.auditDocRetrieveDeferredAckResponse(response.getMessage(), assertion, NhincConstants.AUDIT_LOG_OUTBOUND_DIRECTION, NhincConstants.AUDIT_LOG_NHIN_INTERFACE);

        log.debug("End - sendToRespondingGateway");

        return response;
    }

    /**
     *
     * @param request
     * @param assertion
     * @param homeCommunityId
     * @return
     */
    private DocRetrieveAcknowledgementType serviceDocRetrieveInternal(RetrieveDocumentSetResponseType request, AssertionType assertion, String homeCommunityId, String requestCommunityId) {
        log.debug("Begin DocRetrieveImpl.serviceDocRetrieveInternal");

        DocRetrieveAcknowledgementType response = null;
        HomeCommunityType hcId = new HomeCommunityType();
        String msg = "Adapter doc retrieve deferred response policy check failed.";

        hcId.setHomeCommunityId(homeCommunityId);
        if (isPolicyValidForResponse(request, assertion, hcId)) {
            log.debug("Adapter doc retrieve deferred policy check successful");
            response = sendDocRetrieveDeferredResponseToAgency(request, assertion, requestCommunityId);
        } else {
            msg = "Policy Check Failed on NHIN community : " + homeCommunityId;
            log.error(msg);
            response = createSuccessResponse(NhincConstants.DOC_RETRIEVE_DEFERRED_RESP_ACK_STATUS_MSG);
        }

        log.debug("End DocRetrieveImpl.serviceDocRetrieveInternal");

        return response;
    }

    /**
     *
     * @param request
     * @param assertion
     * @return DocRetrieveAcknowledgementType
     */
    private DocRetrieveAcknowledgementType sendDocRetrieveDeferredResponseToAgency(RetrieveDocumentSetResponseType request, AssertionType assertion, String requestCommunityId) {

        DocRetrieveAcknowledgementType response = null;
        DocRetrieveDeferredAuditLogger auditLog = new DocRetrieveDeferredAuditLogger();
        AdapterDocRetrieveDeferredRespProxy proxy;

        log.debug("Begin DocRetrieveReqImpl.sendDocRetrieveToAgency");
        auditDeferredRetrieveMessage(request, NhincConstants.AUDIT_LOG_OUTBOUND_DIRECTION, NhincConstants.AUDIT_LOG_ADAPTER_INTERFACE, assertion, requestCommunityId);

        proxy = new AdapterDocRetrieveDeferredRespProxyObjectFactory().getAdapterDocRetrieveDeferredResponseProxy();

        response = proxy.sendToAdapter(request, assertion);

        auditLog.auditDocRetrieveDeferredAckResponse(response.getMessage(), assertion, NhincConstants.AUDIT_LOG_INBOUND_DIRECTION, NhincConstants.AUDIT_LOG_ADAPTER_INTERFACE);

        log.debug("End DocRetrieveReqImpl.sendDocRetrieveToAgency");

        return response;
    }

    /**
     *
     * @param request
     * @param direction
     * @param connectInterface
     * @param assertion
     */
    protected void auditDeferredRetrieveMessage(RetrieveDocumentSetResponseType request, String direction, String connectInterface, AssertionType assertion, String requestCommunityId) {

        DocRetrieveResponseMessageType message = new DocRetrieveResponseMessageType();
        message.setRetrieveDocumentSetResponse(request);
        message.setAssertion(assertion);
        AuditRepositoryLogger auditLogger = new AuditRepositoryLogger();
        LogEventRequestType auditLogMsg = auditLogger.logDocRetrieveResult(message, direction, connectInterface);
        if (auditLogMsg != null) {
            auditMessage(auditLogMsg, assertion);
        }

    }
}
