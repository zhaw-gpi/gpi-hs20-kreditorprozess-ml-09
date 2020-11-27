package ch.zhaw.gpi.creditorprocess;

import javax.inject.Named;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.json.JSONObject;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Named("getOrderAndCreditorDataAdapter")
public class GetOrderAndCreditorDataDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Long referenceNr = (Long) execution.getVariable("referenceNr");

        RestTemplate restTemplate = new RestTemplate();

        Boolean orderFound;

        try {
            ResponseEntity<String> response = restTemplate.exchange("http://localhost:8070/api/orders/search/findByReferenceNumber?referenceNumber={referenceNr}", HttpMethod.GET, null, String.class, referenceNr);
            orderFound = true;

            JSONObject orderAsJsonObject = new JSONObject(response.getBody());
            execution.setVariable("orderAmount", orderAsJsonObject.getLong("amount"));
            execution.setVariable("costCenterMgr", orderAsJsonObject.getString("cstCtMgr"));
            String creditorUrl = orderAsJsonObject.getJSONObject("_links").getJSONObject("creditor").getString("href");
            response = restTemplate.exchange(creditorUrl, HttpMethod.GET, null, String.class);

            JSONObject creditorAsJsonObject = new JSONObject(response.getBody());
            execution.setVariable("creditorOrderCount", creditorAsJsonObject.getInt("ordersCnt"));
            execution.setVariable("creditorInvoiceReclamationCount", creditorAsJsonObject.getInt("invoicingReclamationCnt"));
        } catch (Exception e) {
            orderFound = false;
        }

        execution.setVariable("orderFound", orderFound);
    }
    
}
