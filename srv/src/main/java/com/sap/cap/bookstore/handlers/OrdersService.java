package com.sap.cap.bookstore.handlers;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cds.gen.orderservice.OrderItems;
import cds.gen.orderservice.OrderItems_;
import cds.gen.orderservice.OrderService_;
import cds.gen.orderservice.Orders;
import cds.gen.orderservice.Orders_;
import cds.gen.sap.capire.bookstore.Books;
import cds.gen.sap.capire.bookstore.Books_;

import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;

@Component
@ServiceName(OrderService_.CDS_NAME)
public class OrdersService implements EventHandler {

    @Autowired
    PersistenceService db;

    @Before(event = CqnService.EVENT_CREATE, entity = OrderItems_.CDS_NAME)
    public void validateBookAndDecreaseStock(List<OrderItems> items) {
        for(OrderItems item : items) {
            String bookId = item.getBookId();
            Integer amount = item.getAmount();

            //check if the book that should be ordered is available
            CqnSelect sel = Select.from(Books_.class)
                                  .columns(b -> b.stock())
                                  .where(b -> b.ID().eq(bookId));

            Books book = db.run(sel)
                            .first(Books.class)
                            .orElseThrow(() -> new ServiceException(ErrorStatuses.NOT_FOUND, "Book does not exists"));

            //check if order could be fulfilled
            int stock = book.getStock();
            if(stock < amount) {
                throw new ServiceException(ErrorStatuses.BAD_REQUEST, "Not enough Books available");
            }

            //update the book with new stock, new stock eq existing stock value minus amount
            int updatedStock = stock - amount;
            book.setStock(updatedStock);
            CqnUpdate update = Update.entity(Books_.class)
                                     .data(book)
                                     .where(b -> b.ID().eq(bookId));
            db.run(update);
        }
    }

    @Before(event = CqnService.EVENT_CREATE, entity = Orders_.CDS_NAME)
    public void validateBookAndDecreaseStockViaOrders(List<Orders> orders) {
        for(Orders order : orders) {
            List<OrderItems> orderItems = order.getItems();
            if( orderItems != null) {
                validateBookAndDecreaseStock(orderItems);
            }
        }
    }

    @After(event = {CqnService.EVENT_CREATE, CqnService.EVENT_READ}, entity = OrderItems_.CDS_NAME)
    public void calculateNetAmount(List<OrderItems> items) {
        for(OrderItems item : items) {
            String bookId = item.getBookId();

            //get the book that was ordered
            CqnSelect sel = Select.from(Books_.class)
                                  .where(b -> b.ID().eq(bookId));
            Books book = db.run(sel)
                            .single(Books.class);
            
            //calculate the net amount and set inside entity
            BigDecimal finalAmount = book.getPrice().multiply(new BigDecimal(item.getAmount()));
            item.setNetAmount(finalAmount);
        }
    }


}
