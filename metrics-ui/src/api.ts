export interface Metric {
    id: number;
    agentId: number;
    metricType: string;
    avgValue: number;
    periodStart: string;
}

// Тимчасово закоментуємо константу, поки не напишемо реальний fetch()
// const BASE_URL = 'http://localhost:8080/api/v1';

export const api = {
    // Додаємо "_" до параметрів, які поки не використовуються
    login: async (_username: string, _password: string): Promise<string> => {
        return new Promise((resolve) => {
            setTimeout(() => resolve("fake-jwt-token-123"), 500);
        });
    },

    getMetrics: async (_token: string): Promise<Metric[]> => {
        return new Promise((resolve) => {
            setTimeout(() => resolve([
                { id: 1, agentId: 101, metricType: 'CPU', avgValue: 45.5, periodStart: '2026-06-12 10:00:00' },
                { id: 2, agentId: 101, metricType: 'RAM', avgValue: 2048, periodStart: '2026-06-12 10:00:00' },
                { id: 3, agentId: 102, metricType: 'LATENCY', avgValue: 12.4, periodStart: '2026-06-12 10:05:00' },
                { id: 4, agentId: 103, metricType: 'CPU', avgValue: 89.2, periodStart: '2026-06-12 10:10:00' },
            ]), 500);
        });
    }
};
