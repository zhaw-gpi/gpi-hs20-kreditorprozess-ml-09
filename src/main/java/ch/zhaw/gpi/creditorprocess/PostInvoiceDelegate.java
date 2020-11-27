package ch.zhaw.gpi.creditorprocess;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import javax.inject.Named;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Named("postInvoiceAdapter")
public class PostInvoiceDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        // Benötigte Prozessvariablen auslesen
        String creditorName = (String) execution.getVariable("creditorName");
        Long invoiceNr = (Long) execution.getVariable("invoiceNr");
        Long invoiceAmount = (Long) execution.getVariable("invoiceAmount");
        Date invoiceDate = (Date) execution.getVariable("invoiceDate");
        Date dueDate = (Date) execution.getVariable("dueDate");

        // java.util.Date in java.time.LocalDate umwandeln, welches in akzeptiertes
        // "JJJJ-MM-TT" serialisiert wird
        LocalDate invoiceDateLocal = LocalDate.ofInstant(invoiceDate.toInstant(), ZoneId.systemDefault());
        LocalDate dueDateLocal = LocalDate.ofInstant(dueDate.toInstant(), ZoneId.systemDefault());

        // RestTemplate instanzieren
        RestTemplate restTemplate = new RestTemplate();

        try {
            // Request 1 - Kreditor über seinen Namen suchen (GET)
            ResponseEntity<String> response = restTemplate.exchange(
                    "http://localhost:8070/api/creditors/search/findByCrName?crName={crName}", HttpMethod.GET, null,
                    String.class, creditorName);

            // Url der gefundenen Kreditor-Resource auslesen
            JSONObject creditorJsonObject = new JSONObject(response.getBody());
            String creditorUrl = creditorJsonObject.getJSONObject("_links").getJSONObject("self").getString("href");

            // Request 2 - Rechnung einfügen: Body (Rechnung) zusammenbauen
            JSONObject invoiceJsonObject = new JSONObject();
            invoiceJsonObject.put("invoiceId", invoiceNr);
            invoiceJsonObject.put("invoiceAmount", invoiceAmount);

            invoiceJsonObject.put("dateOfInvoice", invoiceDateLocal);
            invoiceJsonObject.put("dateDue", dueDateLocal);

            // Request 2 - Rechnung einfügen: Content-Type im Header setzen und HttpEntity zusammenbauen
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> httpEntity = new HttpEntity<String>(invoiceJsonObject.toString(), headers);

            // Request 2 - Rechnung einfügen: Eigentlicher Request (POST)
            restTemplate.exchange("http://localhost:8070/api/invoices", HttpMethod.POST, httpEntity, String.class);

            // Request 3 & 4: Header auf Uri-List setzen
            headers.setContentType(MediaType.valueOf("text/uri-list"));

            // Request 3 - Zugehöriger Kreditor in Rechnung setzen: HttpEntity besteht aus Url der gefundenen Kreditor-Resource als Body und dem Header
            httpEntity = new HttpEntity<String>(creditorUrl, headers);

            // Request 3 - Zugehöriger Kreditor in Rechnung setzen: Eigentlicher Request (PUT)
            restTemplate.exchange("http://localhost:8070/api/invoices/{invoiceId}/creditor", HttpMethod.PUT, httpEntity,
                    String.class, invoiceNr);

            /**
             * Ab hier nicht in Aufgabenstellung aufgeführt => kein Abzug, falls nicht implementiert
             */
            // Request 4 - Bestellung-Ressourcen-Repräsentation erhalten (GET)
            Long referenceNr = (Long) execution.getVariable("referenceNr");
            response = restTemplate.exchange("http://localhost:8070/api/orders/search/findByReferenceNumber?referenceNumber={referenceNr}", HttpMethod.GET, null, String.class, referenceNr);
            JSONObject orderAsJsonObject = new JSONObject(response.getBody());
            String orderUrl = orderAsJsonObject.getJSONObject("_links").getJSONObject("self").getString("href");

            // Request 5 - Zugehörige Rechnung in Bestellung setzen (PUT) [alternativ könnte man auch zugehörige Bestellung in Rechnung setzen]
            String invoiceUrl = "http://localhost:8070/api/invoices/" + invoiceNr;
            httpEntity = new HttpEntity<String>(invoiceUrl, headers);
            restTemplate.exchange(orderUrl + "/invoice", HttpMethod.PUT, httpEntity, String.class);
        } catch (Exception e) {
            throw e;
        }
    }
}
