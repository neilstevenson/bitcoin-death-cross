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

import React, {useState} from "react";
import SockJsClient from 'react-stomp';
import {useTable} from 'react-table';
import styled from 'styled-components';

const WS_URL = 'http://' + window.location.host + '/hazelcast';
const WS_FEED_PREFIX = '/feed';
const WS_ALERTS = [ WS_FEED_PREFIX + "/alerts" ];

const Styles = styled.div `
  padding: 1rem;
  table {
    border-spacing: 0;
    border: 1px solid gray;
    width: 100%;
    tr {
      :last-child {
        td {
          border-bottom: 0;
        }
      }
    }
    th {
          color: indigo;
          font-size: 12px;
      margin: 0;
      padding: 0.5rem;
      border-bottom: 1px solid gray;
      border-right: 1px solid gray;
      :last-child {
        border-right: 0;
      }
    }
    td {
          color: var(--hazelcast-orange);
          font-size: 12px;
      margin: 0;
      padding: 0.5rem;
      border-bottom: 1px solid gray;
      border-right: 1px solid gray;
      :last-child {
        border-right: 0;
      }
    }
  }
`

function Table({ columns, data }) {
          const {
            getTableProps,
            getTableBodyProps,
            headerGroups,
            rows,
            prepareRow,
          } = useTable({
            columns,
            data,
          })
          
          return (
            <table {...getTableProps()}>
              <thead>
                {headerGroups.map(headerGroup => (
                  <tr {...headerGroup.getHeaderGroupProps()}>
                    {headerGroup.headers.map(column => (
                      <th {...column.getHeaderProps()}>{column.render('Header')}</th>
                    ))}
                  </tr>
                ))}
              </thead>
              <tbody {...getTableBodyProps()}>
                {rows.map((row, i) => {
                  prepareRow(row)
                  return (
                    <tr {...row.getRowProps()}>
                      {row.cells.map(cell => {
                        return <td {...cell.getCellProps()}>{cell.render('Cell')}</td>
                      })}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )
}

function toISO8601(longStr) {
    let dateObj = new Date(Number(longStr));
    return dateObj.toISOString().replace('T',' ').split('.')[0];
}

const columns = [
        {
            Header: 'Timestamp',
            accessor: 'now',
        },
        {
            Header: 'Date',
            accessor: 'date',
        },
        {
            Header: 'Pair',
            accessor: 'pair',
        },
        {
            Header: 'Action',
            accessor: 'action',
        },
        {
            Header: '50 Point',
            accessor: 'average_50_Point',
        },
        {
            Header: '200 Point',
            accessor: 'average_200_Point',
        },
]

const Alerts: React.FunctionComponent = () => {
	const [statealerts, setStatealerts] = useState([]);

	const handleData = (message) => {
	    console.log("Alerts.tsx", "handleData()", message);
		let nowStr = toISO8601(message.now);

        let divClass = "buyColumn";
		let action = "Buy"
		if (message.trend === "Downward") {
			action = "Sell"
			divClass = "sellColumn"
		}
		let action_styled = <div className={divClass}>{action}</div>;
		
    	let alert = {
			now: nowStr,
        	date: message.date,
        	pair: message.pair,
        	action: action_styled,
        	average_50_Point: message.average_50_Point,
        	average_200_Point: message.average_200_Point
    	};
    	if (statealerts.length == 0) {
			setStatealerts([alert])
		} else {
			setStatealerts([alert, ...statealerts])
		}
    };
	
  return (
        <div className="alertsOuterBox">
			<SockJsClient 
            	url={WS_URL}
                topics={WS_ALERTS}
                onMessage={(message) => { handleData(message); }}
                />
            <h6>Alerts</h6>
        	<div className="alertsInnerBox">
    			<Styles>
                    <Table columns={columns} data={statealerts} />
	            </Styles>
        	</div> 
  		</div>
  );	
};

export default Alerts;	