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

import React from 'react';
import SockJsClient from 'react-stomp';
import * as perspective from "@finos/perspective";
import "@finos/perspective-viewer";
import "@finos/perspective-viewer-datagrid";
import "@finos/perspective-viewer-d3fc";

const WS_URL = 'http://' + window.location.host + '/hazelcast';
const WS_FEED_PREFIX = '/feed';
const WS_DATA = [ WS_FEED_PREFIX + "/data" ];

const WORKER = perspective.default.shared_worker();
const CONFIG = {
  plugin: "X/Y Line",
  plugin_config: {},
  settings: false,
  theme: "Vaporwave",
  title: "BTC/USD",
  group_by: [],
  split_by: ["type"],
  columns: ["date","rate","type"],
  filter: [],
  sort: [],
  expressions: [],
  aggregates: {}
};

function init() {
	let rows = [];
  rows.push({
    date: '2017-01-01',
    rate: 995,
    type: 'Current'
  });
  return rows;
}

const TABLE = await WORKER.table(init(), {
  limit: 50000,
});

let count = 0;

class Finos extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
        };
        this.handleData = this.handleData.bind(this);
    }
    	
    handleData(message) {
		if (count < 50000) {
		    console.log("Finos.js", "handleData()", "count==" + count, message);
		}
		count++;
		
		let row = {
        	date: message.date,
        	rate: message.rate,
        	type: message.type,
    };
    let rows = [];
    rows.push(row);

    try {
      TABLE.update(rows);
    } catch (e) {
      console.log("Finos.js", "handleData()", row, e);
    }
  }
	
  render() {	
  	return (
      <div className="finosOuterBox">
			  <SockJsClient 
          url={WS_URL}
          topics={WS_DATA}
          onMessage={this.handleData}
        />
        <div className="finosInnerBox">
			    <perspective-viewer id="perspective"/>
        </div> 
  		</div>
  	);
  }	

	async componentDidMount() {
		console.log("Finos.js", "componentDidMount()");
    let viewer = document.getElementById("perspective");
    
    try {
      let table = await Promise.resolve(TABLE);
      await viewer.load(TABLE);
    } catch (e) {
      console.log("Finos.js", "componentDidMount()", "viewer.load", e);
    }
    
    try {
      viewer.restore(CONFIG);
    } catch (e) {
      console.log("Finos.js", "componentDidMount()", "viewer.restore", e);
    }
  }
};

export default Finos;