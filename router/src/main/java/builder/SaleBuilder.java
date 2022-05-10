/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package builder;

import creator.CompareGroup;
import domain.Sale;
import domain.Summary;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

/**
 *
 * @author raymondhua
 */
public class SaleBuilder extends RouteBuilder {
   @Override
   public void configure()  { 
    from("jms:queue:new-sale")
     .unmarshal().json(JsonLibrary.Gson, Sale.class)
     .to("jms:queue:sale-service");

    from("jms:queue:sale-service")
         .setProperty("customerID").simple("${body.customer.id}")
         .setProperty("customerFirstName").simple("${body.customer.firstName}")
         .setProperty("customerLastName").simple("${body.customer.lastName}") 
         .setProperty("customerGroup").simple("${body.customer.group}") 
         .setProperty("customerEmail").simple("${body.customer.email}") 
         .setProperty("customerUserName").simple("${body.customer.customerCode}")
         .removeHeaders("*") 
         .marshal().json(JsonLibrary.Gson)
         .setHeader(Exchange.HTTP_METHOD, constant("POST"))
         .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
         .to("http://localhost:8083/api/sales")
         .to("jms:queue:customer-summary");

     from("jms:queue:customer-summary")
         .removeHeaders("*") // remove headers to stop them being sent to the service
         .setBody(constant((Object) null)) // doesn't usually make sense to pass a body in a GET request
         .setHeader(Exchange.HTTP_METHOD, constant("GET"))
         .toD("http://localhost:8083/api/sales/customer/${exchangeProperty.customerID}/summary")
         .convertBodyTo(String.class)
         .to("jms:queue:summary-response");

     from("jms:queue:summary-response")
         .log("MARSHAL: ${body}")
         .unmarshal().json(JsonLibrary.Gson, Summary.class)
         .log("UNMARSHAL ${body}")
         .bean(CompareGroup.class, "compare(${exchangeProperty.customerID}, ${exchangeProperty.customerFirstName}, "
                 + "${exchangeProperty.customerLastName}, ${exchangeProperty.customerEmail}, "
                 + "${exchangeProperty.customerUserName}, ${body})")
         .choice()
             .when().simple("${body.group} != ${exchangeProperty.customerGroup}")
                 .to("jms:queue:change-group")
             .otherwise()
                 .to("jms:queue:group-not-changed"); 

     from("jms:queue:change-group")
         .multicast()
         .to("jms:queue:change-group-graphql", "jms:queue:change-group-vend"); 

     from("jms:queue:change-group-graphql")
         .toD("graphql://http://localhost:8082/graphql?query=mutation{changeGroup(id: \"${body.id}\", newGroup: \"${body.group}\") {id, email, username, firstName, lastName, group}}")
         .log("GraphQL service called");
                
   }

}
