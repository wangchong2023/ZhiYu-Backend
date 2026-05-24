import { useEffect, useState, useCallback } from 'react';
import { Table, Button, Space, Tag, Input, Alert } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import configApi from '../../api/configApi';
import type { ConfigHistoryDto } from '../../api/types';

const formatColor: Record<string, string> = { YAML: 'blue', JSON: 'green', PROPERTIES: 'orange' };

function ConfigPage() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<ConfigHistoryDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [groupId, setGroupId] = useState('');
  const [dataId, setDataId] = useState('');

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params: Record<string, unknown> = { page, size };
      if (groupId) params.groupId = groupId;
      if (dataId) params.dataId = dataId;
      const res = await configApi.getConfigHistory(params);
      const body = res.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch {
      setError(t('configPage.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, size, groupId, dataId, t]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const columns = [
    { title: t('common.id'), dataIndex: 'id', width: 80 },
    { title: t('configPage.groupId'), dataIndex: 'groupId' },
    { title: t('configPage.dataId'), dataIndex: 'dataId', ellipsis: true },
    {
      title: t('configPage.format'), dataIndex: 'format', width: 80,
      render: (f: string) => <Tag color={formatColor[f] || 'default'}>{f}</Tag>,
    },
    { title: t('configPage.version'), dataIndex: 'version', width: 60 },
    {
      title: t('configPage.operator'), dataIndex: 'operatorType', width: 80,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    { title: t('configPage.changeSummary'), dataIndex: 'changeSummary', ellipsis: true },
    {
      title: t('configPage.changedAt'), dataIndex: 'createdAt', width: 180,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input placeholder={t('configPage.filterGroupId')} allowClear style={{ width: 180 }}
          prefix={<SearchOutlined />} value={groupId}
          onChange={(e) => { setGroupId(e.target.value); setPage(1); }} />
        <Input placeholder={t('configPage.filterDataId')} allowClear style={{ width: 200 }}
          prefix={<SearchOutlined />} value={dataId}
          onChange={(e) => { setDataId(e.target.value); setPage(1); }} />
        <Button icon={<ReloadOutlined />} onClick={fetchData}>{t('common.refresh')}</Button>
      </Space>
      {error && <Alert type="error" message={error} action={<Button onClick={fetchData}>{t('common.retry')}</Button>} style={{ marginBottom: 16 }} />}
      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={{ current: page, pageSize: size, total, showSizeChanger: true }}
        onChange={(pag) => {
          if (pag.current) setPage(pag.current);
          if (pag.pageSize) setSize(pag.pageSize);
        }} scroll={{ x: 900 }} />
    </div>
  );
}

export default ConfigPage;
