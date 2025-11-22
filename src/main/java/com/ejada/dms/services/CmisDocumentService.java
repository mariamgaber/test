package com.ejada.dms.services;


import com.ejada.commons.errors.exceptions.ErrorCodeException;
import com.emdha.pdfcore.text.pdf.codec.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CmisDocumentService {
    private final CMISUtils cmisUtils;
    @Value("${dms.document.folderPath}")
    private String folderPath;
    @Value("${dms.document.document-class-symbolic-name}")
    private String documentClassSymbolicName;


    public String createDocumentFromContent(Map<String, String> properties, byte[] content, String fileName) {
      try{
          properties.put(PropertyIds.OBJECT_TYPE_ID, documentClassSymbolicName);
          properties.put(PropertyIds.NAME, fileName);
          System.out.println(properties);
        System.out.println("**************Generate parent folder **************");
        Folder parent = (Folder) cmisUtils.getSession().getObjectByPath(folderPath);
        System.out.println("**************Done generating parent folder **************");

        System.out.println("**************Create Document **************");
        Document newDoc = parent.createDocument(properties, cmisUtils.getFileContentStream(content, fileName), VersioningState.MAJOR);
        System.out.println("**************Done Creating Document**************");
       System.out.println("**************Props of Created Document: **************"+newDoc.getProperties());
        return newDoc.getId();
    }catch (CmisBaseException e) {
          System.out.println("**************Error1 Creating Document **************");
          throw new ErrorCodeException("CMIS_FAILED_CREATING_DOCUMENT");
      } catch (Exception e) {
          System.out.println("**************Error2 Creating  Document**************");
          log.error("Unexpected error while creating document: {}", e.getMessage());
          throw new ErrorCodeException("UNEXPECTED_ERROR_CREATING_DOCUMENT");
      }
      }


    // Purpose: Fetches a document by its ID, writes its content to a specified by its path, and prints its properties in XML format.
    //Inputs:
    // docId: The ID of the document to retrieve.
    //downloadPath: The path where the document's content should be saved. If null, no content is downloaded.
    public String retrieveDocumentById(String docId) {
        try {
            Document doc = (Document) cmisUtils.getSession().getObject(cmisUtils.getSession().createObjectId(docId));
            if (doc.getContentStream() == null || doc.getContentStream().getStream() == null) {
                throw new ErrorCodeException("NO_CONTENT_STREAM_AVAILABLE");
            }
            byte[] bytes = doc.getContentStream().getStream().readAllBytes();
            return Base64.encodeBytes(bytes);
        } catch (CmisBaseException e) {
            log.error("Error retrieving document with ID '{}': {}", docId, e.getMessage());
            throw new ErrorCodeException("FAILED_RETRIEVING_DOCUMENT");
        } catch (Exception e) {
            log.error("Unexpected error while retrieving document: {}", e.getMessage());
            throw new ErrorCodeException("UNEXPECTED_ERROR_RETRIEVING_DOCUMENT");
        }
    }
    // Purpose: Updates the content and/or properties of a document by its ID.
    // Inputs:
    // path: The file path of the new content to update.
    //docId: The ID of the document to update.
    //properties: A map containing updated metadata properties.
    public  String updateDocumentById(String path, String docId, Map<String, String> properties) {
        // Retrieves the document by its ID.
        Document doc = (Document) cmisUtils.getSession().getObject(cmisUtils.getSession().createObjectId(docId));
        //Checks out the document to create a private working copy (PWC).
        Document pwc = (Document) cmisUtils.getSession().getObject(doc.checkOut());
        try {
            //Updates the PWC with the new content and properties and checks it in as a new version.
            // true: Indicates the document is being checked in as the next version.
            // properties: A Map<String, String> containing the updated metadata or properties for the document.
            // cmisUtils.getFileContentStream(path, "txt"): A helper method from cmisUtils that creates a ContentStream object from the file located at path. This represents the new content to be stored in the document.
            // "major version": A comment or label indicating this update creates a major version.
            pwc.checkIn(true, properties, cmisUtils.getFileContentStream(path), "major version");
        } catch (CmisBaseException e) {
            e.printStackTrace();
            System.out.println("checkIn failed, trying to cancel the checkout");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //If check-in fails, cancels the checkout and updates the properties only
        pwc.cancelCheckOut();
        pwc.updateProperties(properties);
        //Returns the ID of the updated document.
        return pwc.getId();
    }

    //TODO: Read the following to Understand the idea of CheckOut and CheckIn:
    // Checkout: First, the document is checked out to create a PWC, allowing updates to be made safely without affecting the main document in the repository.
    // Update and Check-In:
    // The PWC is updated with new content (from the provided file path) and metadata (from the properties map).'
    // The changes are saved to the repository as a new version of the document.


    // retrieve documents ids by query
    public  List<String> retrieveDocumentsByQuery(String documentClassSymbolicName, String where) {
        List<String> docsIds = new ArrayList<String>();
        ObjectType type = cmisUtils.getSession().getTypeDefinition(documentClassSymbolicName);
        PropertyDefinition<?> objectIdPropDef = type.getPropertyDefinitions().get(PropertyIds.OBJECT_ID);
        String objectIdQueryName = objectIdPropDef.getQueryName();
        String queryString = "SELECT" + objectIdQueryName + " FROM " + type.getQueryName() + " " + where;
        //execute query
        ItemIterable<QueryResult> results = cmisUtils.getSession().query(queryString, false);
        for (QueryResult qResult : results) {
            String objectid = qResult.getPropertyValueByQueryName(objectIdQueryName);
            docsIds.add(cmisUtils.getSession().createObjectId(objectid).getId());
        }
        return docsIds;

    }
}
