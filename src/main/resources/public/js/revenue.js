/* **********************************************************************
 * 
 *    FETCHERS
 * 
 * *********************************************************************/

function getEndpointFor(resourceType, resourceId) {
    let baseURL = "http://localhost:8580/revenue/";
    if("plans"==resourceType)
        return baseURL + "plans";
    else if("plan"==resourceType)
        return baseURL + "plans/"+resourceId;
    else if("subscriptions"==resourceType)
        return baseURL + "plans/"+resourceId+"/subscriptions";        
    else if("subscription"==resourceType)
        return baseURL + "subscriptions/"+resourceId;        
    else if("statements"==resourceType)
        return baseURL + "subscriptions/"+resourceId+"/statements";        
    else if("bills"==resourceType)
        return baseURL + "subscriptions/"+resourceId+"/bills";        
    else if("bill"==resourceType)
        return baseURL + "bills/"+resourceId;        
    else if("customerBill"==resourceType) {
        // it can be a volatile revenue sharing bill or persisted bill (retrieve from tmf)
        if(resourceId.indexOf("product")!=-1) {
            resourceId = resourceId.replace("customerbill", "revenuebill");
            return baseURL + "bills/"+resourceId+"/cb";        
        }
        else {
            return baseURL + "dev/customerbills/"+resourceId;
        }
    }
    else if("acbrs"==resourceType) {
        // it can be a volatile revenue sharing acbr or persisted acbr (retrieve from tmf)
        if(resourceId.indexOf("product")!=-1) {
            resourceId = resourceId.replace("customerbill", "revenuebill");
            return baseURL + "bills/"+resourceId+"/acbr";        
        }
        else {
            return baseURL + "dev/customerbills/"+resourceId+"/acbr";
        }
    }
    else if("acbr"==resourceType) {
        console.log("ERROR: retrieval of a single ACBR not yet supported. Showing all ACBRs for the corresponding CB");
        resourceId = resourceId.replace("customerbill", "revenuebill");
        return baseURL + "bills/"+resourceId+"/acbr";
    }
    else if("organizations"==resourceType)
        return baseURL + "dev/organizations";
    else if("organizationTransactions"==resourceType)
        return baseURL + "dev/organizations/" + resourceId + "/customerbills";
    else if("purchasedProducts"==resourceType)
        return baseURL + "dev/organizations/" + resourceId + "/purchasedProducts";
    else if("soldProducts"==resourceType)
        return baseURL + "dev/organizations/" + resourceId + "/soldProducts";
    else
        console.log("ERROR: unable to return an endpoint for " + resourceType + ":" + resourceId);
    return null;
}

function genericFetchAndShow(resourceType, resourceId, viewCallback, clickedDOM) {
    let endpoint = getEndpointFor(resourceType, resourceId);
    fetch(endpoint)
        .then(response => response.json())
        .then(data => {
            viewCallback.call(new Object(), data, clickedDOM);
        })
        .catch(error => {
            console.log(error);
        });
}

function fetchAndShowPlans(clickedDOM) {
    genericFetchAndShow("plans", null, showPlans, clickedDOM)
}

function fetchAndShowOrganizations(clickedDOM) {
    genericFetchAndShow("organizations", null, showOrganizations, clickedDOM)
}

function fetchAndShowPlan(clickedDOM) {
    genericFetchAndShow("plan", clickedDOM.getAttribute("objectId"), showPlan, clickedDOM);
}

function fetchAndShowRawPlan(clickedDOM) {
    genericFetchAndShow("plan", clickedDOM.getAttribute("objectId"), showRaw, clickedDOM);
}

function fetchAndShowSubscriptions(clickedDOM) {
    genericFetchAndShow("subscriptions", clickedDOM.getAttribute("objectId"), showSubscriptions, clickedDOM);
}

function fetchAndShowStatements(clickedDOM) {
    genericFetchAndShow("statements", clickedDOM.getAttribute("objectId"), showStatements, clickedDOM);
}

function fetchAndShowBills(clickedDOM) {
    genericFetchAndShow("bills", clickedDOM.getAttribute("objectId"), showBills, clickedDOM);
}

function fetchAndShowCustomerBills(clickedDOM) {
    genericFetchAndShow("bills", clickedDOM.getAttribute("objectId"), fetchAndAddCustomerBills, clickedDOM);
}

function fetchAndShowCustomerBill(clickedDOM) {
    genericFetchAndShow("customerBill", clickedDOM.getAttribute("objectId"), showCustomerBill, clickedDOM);
}

function fetchAndShowACBRs(clickedDOM) {
    genericFetchAndShow("acbrs", clickedDOM.getAttribute("objectId"), showACBRs, clickedDOM);
}

function fetchAndShowTransactions(clickedDOM) {
    genericFetchAndShow("organizationTransactions", clickedDOM.getAttribute("objectId"), showCustomerBills, clickedDOM);
}

function fetchAndShowPurchasedProducts(clickedDOM) {
    genericFetchAndShow("purchasedProducts", clickedDOM.getAttribute("objectId"), showProducts, clickedDOM);
}

function fetchAndShowSoldProducts(clickedDOM) {
    genericFetchAndShow("soldProducts", clickedDOM.getAttribute("objectId"), showProducts, clickedDOM);
}

function fetchAndShowRaw(clickedDOM) {
    showRaw(clickedDOM.getAttribute("objectId"), clickedDOM);
}


/* **********************************************************************
* 
*    CALLBACKS
* 
* *********************************************************************/

function showPlans(plans, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("some title");
    for(var plan of plans)
        getLanes().addToCurrentLane(getNodeFor("plan_short_summary", plan));
}

function showOrganizations(organizations, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("some title");
    for(var org of organizations)
        getLanes().addToCurrentLane(getNodeFor("organization", org));
}

function showPlan(plan, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("a single plan");
    getLanes().addToCurrentLane(getNodeFor("plan_summary", plan));
}

function showSubscriptions(subscriptions, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("some title");
    for(sub of subscriptions)
        getLanes().addToCurrentLane(getNodeFor("subscription_short_summary", sub));
    if(subscriptions.length==0)
        getLanes().addToCurrentLane(getNodeForMessage("No subscriptions found"));
}

function showStatements(statements, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("some title");
    for(var stat of statements)
        getLanes().addToCurrentLane(getNodeFor("statement_short_summary", stat));
    if(statements.length==0)
        getLanes().addToCurrentLane(getNodeForMessage("No statements found"));
}

function showBills(bills, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("some title");
    for(var bill of bills)
        getLanes().addToCurrentLane(getNodeFor("bill_short_summary", bill));
    if(bills.length==0)
        getLanes().addToCurrentLane(getNodeForMessage("No bills found"));
}

function showCustomerBills(customerBills, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("some title");
    for(var cb of customerBills)
        getLanes().addToCurrentLane(getNodeFor("cb_summary", cb));
    if(customerBills.length==0)
        getLanes().addToCurrentLane(getNodeForMessage("No customer bills found"));
}

function showProducts(products, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("some title");
    for(var product of products)
        getLanes().addToCurrentLane(getNodeFor("product", product));
    if(products.length==0)
        getLanes().addToCurrentLane(getNodeForMessage("No products found"));
}

function showCustomerBill(customerBill, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("some title");
    getLanes().addToCurrentLane(getNodeFor("cb_summary", customerBill));
}

function showACBRs(acbrs, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("some title");
    for(var acbr of acbrs) {
        getLanes().addToCurrentLane(getNodeFor("acbr_short_summary", acbr));
    }
    if(acbrs.length==0)
        getLanes().addToCurrentLane(getNodeForMessage("No acbrs found"));
}


function fetchAndAddCustomerBills(revenueBills, clickedDOM) {
    getLanes().cleanAfter(clickedDOM);
    getLanes().pushLane("some title");
    for(var bill of revenueBills)
        genericFetchAndShow("customerBill", bill.id, appendCustomerBill, clickedDOM);
    if(revenueBills.length==0)
        getLanes().addToCurrentLane(getNodeForMessage("No customer bills found"));
}

function appendCustomerBill(customerBill, clickedDOM) {
    getLanes().addToCurrentLane(getNodeFor("cb_summary", customerBill));
}

function showRaw(id, _unused_clickedDOM) {
    let url = null;
    if(id.indexOf("urn:ngsi-ld:customerbill:urn:ngsi-ld:product")!=-1) {
        id = id.replace("customerbill", "revenuebill");
        url = getEndpointFor("customerBill", id);
    }
    else if(id.indexOf("urn:ngsi-ld:revenuebill:urn:ngsi-ld:product")!=-1) {
        url = getEndpointFor("bill", id);
    }
    else if(id.indexOf("urn:ngsi-ld:customer-bill")!=-1) {
        url = getEndpointFor("customerBill", id);
    }
    else if(id.indexOf("urn:ngsi-ld:product")!=-1) {
        url = getEndpointFor("subscription", id);
    }
    else if(id.indexOf("urn:ngsi-ld:plan")!=-1) {
        url = getEndpointFor("plan", id);
    }
    else {
        alert("ERROR: unabble to show raw data for " + id);
    }
    if(url) {
        window.open(url, id).focus();
    }

}

/* **********************************************************************
 * 
 *    DOM Builders
 * 
 * *********************************************************************/

function getTemplateFor(key) {
    let templateCollection = document.getElementById("templates");
    let name = key+"_template";
    return templateCollection.querySelector('[name="'+name+'"]');
}

function getNodeForDate(key, value) {
    let outNode = document.createElement("span");
    outNode.innerHTML = new Date(Date.parse(value)).toISOString(). split('T')[0];
    return outNode;
}

function getNodeForMessage(msg) {
    let outNode = document.createElement("div");
    outNode.classList.add("message");
    outNode.classList.add("tmfbox");
    outNode.innerHTML = msg;
    return outNode;
}

function getNodeForString(key, value) {

    let outNode = null;

    // search for a template
    let template = getTemplateFor(key);
    if(template) {
        // if found, clone it
        outNode = template.cloneNode(true);
    }
    else {
        // if not found, create a span
        outNode = document.createElement("span");
    }
    
    // search for a 'text' sub item and make it a target
    let targetDOM = outNode;
    let textNodes=targetDOM.querySelectorAll('[name="text"]');
    if(textNodes && textNodes.length>0) {
        targetDOM = textNodes[0];
    }

    // set the innterHTML
    targetDOM.innerHTML = value;

    return outNode;
}

function getNodeForArray(key, array) {
    let outNode = null;

    // search for a template
    let template = getTemplateFor(key);
    if(template) {
        outNode = template.cloneNode(true);
    }
    else {
        outNode = document.createElement("div");
    }

    // search for a 'content' sub item and make it a target
    let targetDOM = outNode;
    let contentNodes=targetDOM.querySelectorAll('[name="content"]');
    if(contentNodes && contentNodes.length>0) {
        targetDOM = contentNodes[0];
    }

    // iterate over items and attach nodes to targetDOM
    for(item of array) {
        let n = getNodeFor(key+"_item", item);
        if(n!=null)
            targetDOM.append(n);
    }

    return outNode;
}

function getNodeForObject(key, value) {

    let outNode = null;

    // search for a template
    let template = getTemplateFor(key);
    if(template) {
        outNode = template.cloneNode(true);
    }
    else {
        outNode = document.createElement("div");
    }

    // now, for each value.key which is also in the template, create a node and add it
    for(p in value) {
        // if the property has no value, then skip it
        if(value[p]==null)
            continue;
        // check for a placeholder in the template
        let placeholder = outNode.querySelector('[name="'+p+'"]');
        if(placeholder!=null) {
            // check if there's a template to use
            let templateName = p;
            if(placeholder.getAttribute("use_template")) {
                templateName = placeholder.getAttribute("use_template");
            }
            // now create a node
            let n = getNodeFor(templateName, value[p]);
            if(n!=null) {
                placeholder.append(n);
            } else {
                // remove also the container
                placeholder.parentNode.removeChild(placeholder);
            }
        }
    }

    if(value.id) {
        outNode.setAttribute("objectId", value.id);
    }

    return outNode;
}

function getNodeFor(key, value) {
    if(Date.parse(value) && value.indexOf && value.indexOf("T")!=-1) {
        return getNodeForDate(key, value);
    }
    else if(value.substring || typeof value === 'number' ) {
        return getNodeForString(key, value);
    }
    else if(Array.isArray(value)) {
        if(value.length>0)
            return getNodeForArray(key, value);
    }
    else if(value && typeof(value) === 'object') {
        return getNodeForObject(key, value);
    }
    else {
        console.log("ERROR: unable to create an object for " + key + ". unknown object type. Value is "+value);
    }
    return null;
}


/* **********************************************************************
* 
*    LANES MANAGEMENT
* 
* *********************************************************************/


class Lanes {

    constructor(containerDOM) {
        this.dom = this._createDOM();
        containerDOM.appendChild(this.dom);
        this.currentCol = -1;
    }

    addMenuEntry(label, callback) {
        let button = document.createElement("button");
        button.classList.add("menuButton");
        button.innerHTML = label;
        if(callback)
            button.onclick = callback;
        else {
            button.setAttribute("disabled", true);
            button.style.display="none";
        }
        this.menuCol.appendChild(button);
        return button;
    }

    addMenuGroup(label) {
        let h4 = document.createElement("h4");
        h4.innerHTML = label;
        this.menuCol.appendChild(h4);
        return h4;
    }

    addMenuSeparator() {
        let hr = document.createElement("hr");
        this.menuCol.appendChild(hr);
        return hr;
    }

    pushLane(columnLabel) {
        let col = this._createCol();
        this.currentCol++;
        col.setAttribute("name", "lane_"+this.currentCol);
        col.classList.add("lane");
        this.row.appendChild(col);
        return col;
    }

    popLane() {
        let laneDOM = this._getLane(this.currentCol);
        if(laneDOM) {
            laneDOM.parentNode.removeChild(laneDOM);
            this.currentCol--;
            return laneDOM;
        }
    }

    addToCurrentLane(dom) {
        let laneDOM = this.row.querySelector('[name="lane_'+this.currentCol+'"');
        if(laneDOM)
            laneDOM.appendChild(dom);
    }

    // revoves all lanes after the one containing dom
    cleanAfter(dom) {
        let domLane = this._getLaneContaining(dom);
        while(this._getLastLane()!=domLane) {
            this.popLane();
        }
    }

    // returns -1 if not found
    _getLaneNumberContaining(dom) {
        let lane = this._getLaneNumberContaining(dom);
        if(dom)
            return new Number(dom.getAttribute("name"));
        else
            return -1;
    }

    _getLaneContaining(dom) {
        while(dom && dom.parentNode!=this.row) {
            dom = dom.parentNode;
        }
        if(dom)
            return dom;
    }

    _getLane(nr) {
        let laneDOM = this.row.querySelector('[name="lane_'+nr+'"');
        return laneDOM;
    } 

    _getLastLane() {
        return this._getLane(this.currentCol);
    }

    _createDOM() {
        let table = document.createElement("table");
        let tr = document.createElement("tr");    
        this.row = tr;
        this.menuCol = this._createCol();
        this.menuCol.setAttribute("name", "menu");
        this.menuCol.classList.add("menu");
        tr.appendChild(this.menuCol);
        table.appendChild(tr);
        return table;
    }

    _createCol() {
        let td = document.createElement("td");
        return td;
    }

}

var lanes;

function getLanes() {
    if(lanes==null) {
        lanes = new Lanes(document.getElementById("lanesContainer"));

        lanes.addMenuGroup("Revenue Sharing");
        lanes.addMenuEntry("Plans", fetchAndShowPlans);
        lanes.addMenuEntry("Subscriptions");

        lanes.addMenuGroup("TMF 632 Party");
        lanes.addMenuEntry("Organizations", fetchAndShowOrganizations);
        lanes.addMenuEntry("Individuals");
//        lanes.addMenuGroup("TMF 620 Catalog");
        lanes.addMenuEntry("Service Specs");
        lanes.addMenuEntry("Resource Specs");
        lanes.addMenuEntry("Product Specs");
//        lanes.addMenuSeparator();
        lanes.addMenuEntry("Product offerings");
        lanes.addMenuEntry("Product offering prices");
//        lanes.addMenuGroup("TMF 622 Order");
//        lanes.addMenuEntry("...");
//        lanes.addMenuGroup("TMF 637 Inventory");
//        lanes.addMenuEntry("...");
    }

    return lanes;

}