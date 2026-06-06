package com.suivia.invoice.service;

import com.suivia.invoice.dto.NFeExtractedData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser nativo de XML para NFe/CTe.
 * Lê as tags diretamente sem custo de AWS Textract.
 * Conforme RF04 do documento SUIVIA.
 *
 * Tags extraídas:
 * <emit><CNPJ>       → CNPJ do emitente
 * <dest><CNPJ>       → CNPJ do destinatário
 * <nNF>              → Número da nota fiscal
 * <dhEmi>            → Data/hora de emissão
 * <vNF>              → Valor total da nota
 * <chNFe>            → Chave de acesso (44 dígitos)
 * <xPed>             → Número do Pedido de Compra
 * <det>              → Itens da nota
 */
@Slf4j
@Service
public class NFeXmlParserService {

    public NFeExtractedData parse(InputStream xmlInputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false); // Ignora namespace para facilitar extração
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlInputStream);
            doc.getDocumentElement().normalize();

            NFeExtractedData data = new NFeExtractedData();

            // CNPJ emitente (fornecedor)
            data.setSupplierCnpj(getTagValue(doc, "emit", "CNPJ"));

            // CNPJ destinatário
            data.setRecipientCnpj(getTagValue(doc, "dest", "CNPJ"));

            // Número da NF
            data.setInvoiceNumber(getFirstTagValue(doc, "nNF"));

            // Chave de acesso NFe (44 dígitos) - RF05
            data.setInvoiceKey(getFirstTagValue(doc, "chNFe"));

            // Data de emissão
            data.setEmissionDate(getFirstTagValue(doc, "dhEmi"));

            // Valor total
            String vNF = getFirstTagValue(doc, "vNF");
            if (vNF != null && !vNF.isBlank()) {
                data.setTotalAmount(new BigDecimal(vNF));
            }

            // Pedido de Compra (RF07 - Identificação Automática do Pedido)
            data.setPurchaseOrderRef(getFirstTagValue(doc, "xPed"));

            // Itens da nota
            data.setExtractedItems(extractItems(doc));

            log.info("XML NFe parseado com sucesso | NF={} | CNPJ={} | Valor={}",
                    data.getInvoiceNumber(), data.getSupplierCnpj(), data.getTotalAmount());

            return data;

        } catch (Exception e) {
            log.error("Erro ao parsear XML NFe: {}", e.getMessage(), e);
            throw new RuntimeException("Falha no parsing do XML NFe: " + e.getMessage(), e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String getTagValue(Document doc, String parentTag, String childTag) {
        NodeList parentNodes = doc.getElementsByTagName(parentTag);
        if (parentNodes.getLength() > 0) {
            NodeList children = parentNodes.item(0).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (childTag.equals(children.item(i).getNodeName())) {
                    return children.item(i).getTextContent().trim();
                }
            }
        }
        return null;
    }

    private String getFirstTagValue(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private List<NFeExtractedData.InvoiceItem> extractItems(Document doc) {
        List<NFeExtractedData.InvoiceItem> items = new ArrayList<>();
        NodeList detNodes = doc.getElementsByTagName("det");

        for (int i = 0; i < detNodes.getLength(); i++) {
            NFeExtractedData.InvoiceItem item = new NFeExtractedData.InvoiceItem();

            Document itemDoc = detNodes.item(i).getOwnerDocument();
            NodeList prodNodes = detNodes.item(i).getChildNodes();

            for (int j = 0; j < prodNodes.getLength(); j++) {
                if ("prod".equals(prodNodes.item(j).getNodeName())) {
                    NodeList prodChildren = prodNodes.item(j).getChildNodes();
                    for (int k = 0; k < prodChildren.getLength(); k++) {
                        String name = prodChildren.item(k).getNodeName();
                        String value = prodChildren.item(k).getTextContent().trim();
                        switch (name) {
                            case "xProd" -> item.setDescription(value);
                            case "qCom"  -> item.setQuantity(new BigDecimal(value));
                            case "vUnCom"-> item.setUnitPrice(new BigDecimal(value));
                            case "NCM"   -> item.setNcmCode(value);
                            case "xPed"  -> item.setPurchaseOrderRef(value);
                            case "nItemPed" -> item.setOrderItemRef(value);
                        }
                    }
                }
            }
            items.add(item);
        }
        return items;
    }
}
