/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.crypto.store.internal;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.crypto.AsymmetricKeyFactory;
import org.xwiki.crypto.password.PrivateKeyPasswordBasedEncryptor;
import org.xwiki.crypto.pkix.params.CertifiedKeyPair;
import org.xwiki.crypto.pkix.params.CertifiedPublicKey;
import org.xwiki.crypto.pkix.params.x509certificate.X509CertifiedPublicKey;
import org.xwiki.crypto.store.CertificateStoreException;
import org.xwiki.crypto.store.KeyStore;
import org.xwiki.crypto.store.KeyStoreException;
import org.xwiki.crypto.store.internal.query.CertificateObjectReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * X509 implementation of {@link org.xwiki.crypto.store.KeyStore}.
 *
 * @version $Id$
 * @since 6.0
 */
@Component
@Named("X509")
@Singleton
public class X509KeyStore extends AbstractX509Store implements KeyStore
{
    /**
     * Space where the certificate class is stored.
     */
    public static final String PRIVATEKEYCLASS_SPACE = "Crypto";

    /**
     * Document name of the certificate class.
     */
    public static final String PRIVATEKEYCLASS_NAME = "PrivateKeyClass";

    /**
     * Full name of the certificate class.
     */
    public static final String PRIVATEKEYCLASS_FULLNAME = PRIVATEKEYCLASS_SPACE + "." + PRIVATEKEYCLASS_NAME;

    /**
     * Reference to the signature class.
     */
    public static final LocalDocumentReference PRIVATEKEYCLASS =
        new LocalDocumentReference(PRIVATEKEYCLASS_SPACE, PRIVATEKEYCLASS_NAME);

    /**
     * Name of the key field.
     */
    public static final String PRIVATEKEYCLASS_PROP_KEY = "key";

    @Inject
    private PrivateKeyPasswordBasedEncryptor encryptor;

    @Inject
    private AsymmetricKeyFactory keyFactory;

    @Override
    public void store(EntityReference store, CertifiedKeyPair keyPair) throws KeyStoreException
    {
        storeKeyPair(store, keyPair.getCertificate(), keyPair.getPrivateKey().getEncoded());
    }

    @Override
    public void store(EntityReference store, CertifiedKeyPair keyPair, byte[] password) throws KeyStoreException
    {
        byte[] key;

        try {
            key = encryptor.encrypt(password, keyPair.getPrivateKey());
        } catch (Exception e) {
            throw new KeyStoreException("Error while encrypting private key to store a key pair in [" + store + "]", e);
        }

        storeKeyPair(store, keyPair.getCertificate(), key);
    }

    private void storeKeyPair(EntityReference store, CertifiedPublicKey certificate, byte[] privateKey)
        throws KeyStoreException
    {
        XWikiContext context = getXWikiContext();
        XWikiDocument document;

        try {
            document = storeCertificate(store, certificate, context);
        } catch (CertificateStoreException e) {
            throw new KeyStoreException("Error while preparing certificate to store a key pair in [" + store + "]", e);
        }

        try {
            BaseObject obj = document.getXObject(PRIVATEKEYCLASS);

            if (obj == null) {
                obj = document.newXObject(PRIVATEKEYCLASS, context);
            }

            obj.setLargeStringValue(PRIVATEKEYCLASS_PROP_KEY, getEncoder().encode(privateKey, 64));

            context.getWiki().saveDocument(document, context);
        } catch (IOException e) {
            throw new KeyStoreException("Error while preparing private key for ["
                + document.getDocumentReference() + "]", e);
        } catch (XWikiException e) {
            throw new KeyStoreException("Error while saving key pair for ["
                + document.getDocumentReference() + "]", e);
        }
    }

    @Override
    public CertifiedKeyPair retrieve(EntityReference entity) throws KeyStoreException
    {
        return retrieve(entity, (byte[]) null);
    }

    @Override
    public CertifiedKeyPair retrieve(EntityReference entity, byte[] password) throws KeyStoreException
    {
        XWikiContext context = getXWikiContext();

        try {
            XWikiDocument document = context.getWiki().getDocument(getDocumentReference(entity), context);
            BaseObject certObj = document.getXObject(X509CertificateStore.CERTIFICATECLASS);
            BaseObject pkObj = document.getXObject(PRIVATEKEYCLASS);

            if (pkObj == null || certObj == null) {
                return null;
            }

            byte[] cert = getEncoder().decode(
                certObj.getLargeStringValue(X509CertificateStore.CERTIFICATECLASS_PROP_CERTIFICATE));
            byte[] key = getEncoder().decode(pkObj.getLargeStringValue(PRIVATEKEYCLASS_PROP_KEY));

            if (password != null) {
                return new CertifiedKeyPair(encryptor.decrypt(password, key), getCertificateFactory().decode(cert));
            } else {
                return new CertifiedKeyPair(keyFactory.fromPKCS8(key), getCertificateFactory().decode(cert));
            }
        } catch (Exception e) {
            throw new KeyStoreException("Failed to retrieved private key from [" + entity + "]");
        }
    }

    @Override
    public CertifiedKeyPair retrieve(EntityReference store, CertifiedPublicKey publicKey) throws KeyStoreException
    {
        return retrieve(store, publicKey, null);
    }

    @Override
    public CertifiedKeyPair retrieve(EntityReference store, CertifiedPublicKey certificate, byte[] password)
        throws KeyStoreException
    {
        if (!(certificate instanceof X509CertifiedPublicKey)) {
            throw new IllegalArgumentException("Certificate should be X509 certificates.");
        }

        X509CertifiedPublicKey publicKey = (X509CertifiedPublicKey) certificate;
        XWikiContext context = getXWikiContext();

        try {
            CertificateObjectReference certRef = findCertificate(store, publicKey);

            if (certRef == null) {
                return null;
            }

            XWikiDocument document = getDocument(store, certRef, context);
            BaseObject pkObj = document.getXObject(PRIVATEKEYCLASS);

            if (pkObj == null) {
                return null;
            }

            byte[] key = getEncoder().decode(pkObj.getLargeStringValue(PRIVATEKEYCLASS_PROP_KEY));

            if (password != null) {
                return new CertifiedKeyPair(encryptor.decrypt(password, key), certificate);
            } else {
                return new CertifiedKeyPair(keyFactory.fromPKCS8(key), certificate);
            }
        } catch (Exception e) {
            throw new KeyStoreException("Failed to retrieved private key for certificate ["
                + publicKey.getSubject().getName() + "]");
        }
    }
}
