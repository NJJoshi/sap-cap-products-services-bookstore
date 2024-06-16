using { sap.capire.bookstore as db} from '../db/schema';

//Define Book Service
service BooksService {
    @readonly entity Books as projection on db.Books{ * , category as genre } excluding { category, createdBy, createdAt, modifiedBy, modifiedAt };
    @readonly entity Authors as projection on db.Authors;
}

//Define Order Service
service OrderService {
    entity Orders as projection on db.Orders;
    entity OrderItems as projection on db.OrderItems;
}

//Reusing Admin Services
using { AdminService } from '@sap/capire-products';
extend service AdminService with {
    entity Authors as projection on db.Authors;
}

//AdminService will only be accessible by role['Administrators'] users
annotate AdminService @(requires: 'Administrators');
