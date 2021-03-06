/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.ws.security.policy.model;


import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.cxf.ws.policy.PolicyBuilder;
import org.apache.cxf.ws.policy.builder.primitive.PrimitiveAssertion;
import org.apache.cxf.ws.security.policy.SP12Constants;
import org.apache.cxf.ws.security.policy.SPConstants;
import org.apache.neethi.All;
import org.apache.neethi.ExactlyOne;
import org.apache.neethi.Policy;
import org.apache.neethi.PolicyComponent;

public class AsymmetricBinding extends SymmetricAsymmetricBindingBase {

    private InitiatorToken initiatorToken;
    
    private InitiatorSignatureToken initiatorSignatureToken;
    
    private InitiatorEncryptionToken initiatorEncryptionToken;

    private RecipientToken recipientToken;
    
    private RecipientSignatureToken recipientSignatureToken;
    
    private RecipientEncryptionToken recipientEncryptionToken;

    public AsymmetricBinding(SPConstants version, PolicyBuilder b) {
        super(version, b);
    }

    /**
     * @return Returns the initiatorToken.
     */
    public InitiatorToken getInitiatorToken() {
        return initiatorToken;
    }

    /**
     * @param initiatorToken The initiatorToken to set.
     */
    public void setInitiatorToken(InitiatorToken initiatorToken) {
        this.initiatorToken = initiatorToken;
    }
    
    /**
     * @return Returns the initiatorSignatureToken.
     */
    public InitiatorSignatureToken getInitiatorSignatureToken() {
        return initiatorSignatureToken;
    }

    /**
     * @param initiatorSignatureToken The initiatorSignatureToken to set.
     */
    public void setInitiatorSignatureToken(InitiatorSignatureToken initiatorSignatureToken) {
        this.initiatorSignatureToken = initiatorSignatureToken;
    }
    
    /**
     * @return Returns the initiatorEncryptionToken.
     */
    public InitiatorEncryptionToken getInitiatorEncryptionToken() {
        return initiatorEncryptionToken;
    }

    /**
     * @param initiatorEncryptionToken The initiatorEncryptionToken to set.
     */
    public void setInitiatorEncryptionToken(InitiatorEncryptionToken initiatorEncryptionToken) {
        this.initiatorEncryptionToken = initiatorEncryptionToken;
    }
    
    /**
     * @return Returns the recipientToken.
     */
    public RecipientToken getRecipientToken() {
        return recipientToken;
    }

    /**
     * @param recipientToken The recipientToken to set.
     */
    public void setRecipientToken(RecipientToken recipientToken) {
        this.recipientToken = recipientToken;
    }

    /**
     * @return Returns the recipientSignatureToken.
     */
    public RecipientSignatureToken getRecipientSignatureToken() {
        return recipientSignatureToken;
    }

    /**
     * @param recipientSignatureToken The recipientSignatureToken to set.
     */
    public void setRecipientSignatureToken(RecipientSignatureToken recipientSignatureToken) {
        this.recipientSignatureToken = recipientSignatureToken;
    }    

    /**
     * @return Returns the recipientEncryptionToken.
     */
    public RecipientEncryptionToken getRecipientEncryptionToken() {
        return recipientEncryptionToken;
    }

    /**
     * @param recipientEncryptionToken The recipientEncryptionToken to set.
     */
    public void setRecipientEncryptionToken(RecipientEncryptionToken recipientEncryptionToken) {
        this.recipientEncryptionToken = recipientEncryptionToken;
    }        
    
    public QName getRealName() {
        return constants.getAsymmetricBinding();
    }
    public QName getName() {
        return SP12Constants.INSTANCE.getAsymmetricBinding();
    }
    public PolicyComponent normalize() {
        return this;
    }
    public Policy getPolicy() {
        Policy p = new Policy();
        ExactlyOne ea = new ExactlyOne();
        p.addPolicyComponent(ea);
        All all = new All();
        
        /*
        asymmetricBinding.setAlgorithmSuite(algorithmSuite);
        asymmetricBinding.setProtectionOrder(getProtectionOrder());
        asymmetricBinding.setSignatureProtection(isSignatureProtection());
        asymmetricBinding.setSignedEndorsingSupportingTokens(getSignedEndorsingSupportingTokens());
        asymmetricBinding.setTokenProtection(isTokenProtection());
        */
        if (getInitiatorToken() != null) {
            all.addPolicyComponent(getInitiatorToken());
        }
        if (getInitiatorSignatureToken() != null) {
            all.addPolicyComponent(getInitiatorSignatureToken());
        }
        if (getInitiatorEncryptionToken() != null) {
            all.addPolicyComponent(getInitiatorEncryptionToken());
        }
        if (getRecipientToken() != null) {
            all.addPolicyComponent(getRecipientToken());
        }
        if (getRecipientSignatureToken() != null) {
            all.addPolicyComponent(getRecipientSignatureToken());
        }
        if (getRecipientEncryptionToken() != null) {
            all.addPolicyComponent(getRecipientEncryptionToken());
        }
        /*
        if (isEntireHeadersAndBodySignatures()) {
            all.addPolicyComponent(new PrimitiveAssertion(SP12Constants.ONLY_SIGN_ENTIRE_HEADERS_AND_BODY));
        }
        */
        if (isIncludeTimestamp()) {
            all.addPolicyComponent(new PrimitiveAssertion(SP12Constants.INCLUDE_TIMESTAMP));
        }
        if (getLayout() != null) {
            all.addPolicyComponent(getLayout());
        }
        ea.addPolicyComponent(all);
        Policy pc = p.normalize(builder.getPolicyRegistry(), true);
        if (pc != null) {
            return pc;
        } else {
            return new Policy();
        }
    }

    public void serialize(XMLStreamWriter writer) throws XMLStreamException {
        String localname = getRealName().getLocalPart();
        String namespaceURI = getRealName().getNamespaceURI();

        String prefix = writer.getPrefix(namespaceURI);

        if (prefix == null) {
            prefix = getRealName().getPrefix();
            writer.setPrefix(prefix, namespaceURI);
        }

        // <sp:AsymmetricBinding>
        writer.writeStartElement(prefix, localname, namespaceURI);
        writer.writeNamespace(prefix, namespaceURI);

        String pPrefix = writer.getPrefix(SPConstants.POLICY.getNamespaceURI());
        if (pPrefix == null) {
            pPrefix = SPConstants.POLICY.getPrefix();
            writer.setPrefix(pPrefix, SPConstants.POLICY.getNamespaceURI());
        }

        // <wsp:Policy>
        writer.writeStartElement(pPrefix, SPConstants.POLICY.getLocalPart(), SPConstants.POLICY
            .getNamespaceURI());

        if (initiatorToken == null && initiatorSignatureToken == null) {
            throw new RuntimeException("InitiatorToken or InitiatorSignatureToken is not set");
        }

        if (initiatorToken != null) {
            // <sp:InitiatorToken>
            initiatorToken.serialize(writer);
            // </sp:InitiatorToken>
        }
        
        if (initiatorSignatureToken != null) {
            // <sp:InitiatorSignatureToken>
            initiatorSignatureToken.serialize(writer);
            // </sp:InitiatorSignatureToken>
        }
        
        if (initiatorEncryptionToken != null) {
            // <sp:InitiatorEncryptionToken>
            initiatorEncryptionToken.serialize(writer);
            // </sp:InitiatorEncryptionToken>
        }
        
        if (recipientToken == null && recipientSignatureToken == null) {
            throw new RuntimeException("RecipientToken or RecipientSignatureToken is not set");
        }

        if (recipientToken != null) {
            // <sp:RecipientToken>
            recipientToken.serialize(writer);
            // </sp:RecipientToken>
        }
        
        if (recipientSignatureToken != null) {
            // <sp:RecipientSignatureToken>
            recipientSignatureToken.serialize(writer);
            // </sp:RecipientSignatureToken>
        }
        
        if (recipientEncryptionToken != null) {
            // <sp:RecipientEncryptionToken>
            recipientEncryptionToken.serialize(writer);
            // </sp:RecipientEncryptionToken>
        }

        AlgorithmSuite algorithmSuite = getAlgorithmSuite();
        if (algorithmSuite == null) {
            throw new RuntimeException("AlgorithmSuite is not set");
        }

        // <sp:AlgorithmSuite>
        algorithmSuite.serialize(writer);
        // </sp:AlgorithmSuite>

        Layout layout = getLayout();
        if (layout != null) {
            // <sp:Layout>
            layout.serialize(writer);
            // </sp:Layout>
        }

        if (isIncludeTimestamp()) {
            // <sp:IncludeTimestamp>
            writer.writeStartElement(prefix, SPConstants.INCLUDE_TIMESTAMP, namespaceURI);
            writer.writeEndElement();
            // </sp:IncludeTimestamp>
        }

        if (SPConstants.ProtectionOrder.EncryptBeforeSigning.equals(getProtectionOrder())) {
            // <sp:EncryptBeforeSign />
            writer.writeStartElement(prefix, SPConstants.ProtectionOrder.EncryptBeforeSigning.toString(),
                                     namespaceURI);
            writer.writeEndElement();
        }

        if (isSignatureProtection()) {
            // <sp:EncryptSignature />
            // FIXME move the String constants to a QName
            writer.writeStartElement(prefix, SPConstants.ENCRYPT_SIGNATURE, namespaceURI);
            writer.writeEndElement();
        }

        if (isTokenProtection()) {
            // <sp:ProtectTokens />
            writer.writeStartElement(prefix, SPConstants.PROTECT_TOKENS, namespaceURI);
            writer.writeEndElement();
        }

        if (isEntireHeadersAndBodySignatures()) {
            // <sp:OnlySignEntireHeaderAndBody />
            writer.writeStartElement(prefix, SPConstants.ONLY_SIGN_ENTIRE_HEADERS_AND_BODY, namespaceURI);
            writer.writeEndElement();
        }

        // </wsp:Policy>
        writer.writeEndElement();

        // </sp:AsymmetircBinding>
        writer.writeEndElement();
    }
}
