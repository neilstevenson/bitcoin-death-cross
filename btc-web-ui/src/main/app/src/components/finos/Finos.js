/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import SockJsClient from 'react-stomp';
import * as perspective from "@finos/perspective";
import "@finos/perspective-viewer";
//import PerspectiveViewerConfig from "@finos/perspective-viewer";
import {
    HTMLPerspectiveViewerElement,
} from "@finos/perspective-viewer";
import "@finos/perspective-viewer-datagrid";

const WS_URL = 'http://' + window.location.host + '/hazelcast';
const WS_FEED_PREFIX = '/feed';
const WS_DATA = [ WS_FEED_PREFIX + "/data" ];

const worker = perspective.default.shared_worker();
const config : any/*FIXME: PerspectiveViewerConfig*/ = {
	//FIXME MOVE CONFIG TO HERE
};

function init() {
	let rows = [];
	rows.push({
        	date: '2017-01-01',
        	pair: 'BTCUSD',
        	rate: 0.0,
        	type: 'Current',
	})
    return rows;
}

const initTable = async (): Promise<perspective.Table> => {
	return await worker.table(init());
};
//const updateTable = async (): Promise<perspective.Table> => {
//	return await worker.table(newRows());
//};

const Finos = (): React.ReactElement => {
    const viewer = React.useRef<HTMLPerspectiveViewerElement>(null);
    let count = 0;

	const handleData = (message: any) => {
		if (count == 0) {
		    console.log("Finos.tsx", "handleData()", "count==" + count, message);
		    count++;
		}
	    
		let row = {
        	date: message.date,
        	pair: message.pair,
        	rate: message.rate,
        	type: message.type,
    	};

        //updateTable().then((table) => {
		//	if (viewer.current) {
		//		const t = Promise.resolve<perspective.Table>(table)
		//		//XXX t.update(row)				
		//	}
        //})
	}
        
    React.useEffect(() => {
        initTable().then((table) => {
            if (viewer.current) {
				const t = Promise.resolve(table)
				viewer.current.load(t)
				viewer.current.restore(
{
  "plugin": "Datagrid",
  "plugin_config": {
    "columns": {
      "rate": {
        "gradient": 10,
        "number_color_mode": "bar"
      }
    },
    "editable": false,
    "scroll_lock": true
  },
  "settings": true,
  "group_by": [
  ],
  "split_by": [
  ],
  "columns": [
    "date",
    "pair",
    "rate",
    "type"
  ],
  "filter": [
  ],
  "sort": [
  ],
  "expressions": [
  ],
  "aggregates": {
  }
}
				);
				viewer.current.toggleConfig();
            }
        })
    }, []);	
	
  return (
        <div className="finosOuterBox">
			<SockJsClient 
            	url={WS_URL}
                topics={WS_DATA}
                onMessage={(message) => { handleData(message); }}
                />
        	<div className="finosInnerBox">
    		  <perspective-viewer ref={viewer}/>
        	</div> 
  		</div>
  );	
};

export default Finos;	