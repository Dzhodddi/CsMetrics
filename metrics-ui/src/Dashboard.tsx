import React, { useEffect, useState } from 'react';
import {api, type Metric} from './api';

interface DashboardProps {
    token: string;
    onLogout: () => void;
}

export const Dashboard: React.FC<DashboardProps> = ({ token, onLogout }) => {
    const [metrics, setMetrics] = useState<Metric[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchMetrics = async () => {
            const data = await api.getMetrics(token);
            setMetrics(data);
            setLoading(false);
        };
        fetchMetrics();
    }, [token]);

    return (
        <div className="dashboard-container">
            <header className="dashboard-header">
                <h1>Aggregated metrics</h1>
                <button onClick={onLogout} className="logout-btn">Вийти</button>
            </header>

            {loading ? (
                <p>Downloading...</p>
            ) : (
                <div className="table-wrapper">
                    <table className="metrics-table">
                        <thead>
                        <tr>
                            <th>Record ID</th>
                            <th>Agent ID</th>
                            <th>Metric type</th>
                            <th>Average value</th>
                            <th>Time</th>
                        </tr>
                        </thead>
                        <tbody>
                        {metrics.map(metric => (
                            <tr key={metric.id}>
                                <td>{metric.id}</td>
                                <td>{metric.agentId}</td>
                                <td>
                                        <span className={`badge ${metric.metricType.toLowerCase()}`}>
                                            {metric.metricType}
                                        </span>
                                </td>
                                <td>{metric.avgValue}</td>
                                <td>{metric.periodStart}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};