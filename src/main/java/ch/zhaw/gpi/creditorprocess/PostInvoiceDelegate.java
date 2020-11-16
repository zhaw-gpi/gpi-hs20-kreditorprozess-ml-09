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
import org.springframework.http.HttpStatus;
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

        // java.util.Date in java.time.LocalDate umwandeln, welches in akzeptiertes "JJJJ-MM-TT" serialisiert wird
        LocalDate invoiceDateLocal = LocalDate.ofInstant(invoiceDate.toInstant(), ZoneId.systemDefault());
        LocalDate dueDateLocal = LocalDate.ofInstant(dueDate.toInstant(), ZoneId.systemDefault());

        // RestTemplate instanzieren
        RestTemplate restTemplate = new RestTemplate();

        // Request 1: Kreditor über seinen Namen suchen (GET)
        ResponseEntity<String> response = restTemplate.exchange("http://localhost:8070/api/creditors/search/findByCrName?crName={crName}", HttpMethod.GET, null, String.class, creditorName);
        
        if(response.getStatusCode().equals(HttpStatus.OK)){
            // Url der gefundenen Kreditor-Resource auslesen
            JSONObject creditorJsonObject = new JSONObject(response.getBody());
            String creditorUrl = creditorJsonObject.getJSONObject("_links").getJSONObject("self").getString("href");

            // Request 2: Body (Rechnung) zusammenbauen
            JSONObject invoiceJsonObject = new JSONObject();
            invoiceJsonObject.put("invoiceId", invoiceNr);
            invoiceJsonObject.put("invoiceAmount", invoiceAmount);
            
            invoiceJsonObject.put("dateOfInvoice", invoiceDateLocal);
            invoiceJsonObject.put("dateDue", dueDateLocal);

            // Request 2: Content-Type im Header setzen und HttpEntity zusammenbauen
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> httpEntity = new HttpEntity<String>(invoiceJsonObject.toString(), headers);

            // Request 2: Eigentlicher Request (POST)
            response = restTemplate.exchange("http://localhost:8070/api/invoices", HttpMethod.POST, httpEntity, String.class);

            if(response.getStatusCode().equals(HttpStatus.CREATED)){
                // Request 3: Header auf Uri-List setzen
                headers.setContentType(MediaType.valueOf("text/uri-list"));

                // Request 3: HttpEntity besteht aus Url der gefundenen Kreditor-Resource als Body und dem Header
                httpEntity = new HttpEntity<String>(creditorUrl, headers);

                // Request 3: Eigentlicher Request (PUT)
                response = restTemplate.exchange("http://localhost:8070/api/invoices/{invoiceId}/creditor", HttpMethod.PUT, httpEntity, String.class, invoiceNr);
            }
        } 
    }    
}
