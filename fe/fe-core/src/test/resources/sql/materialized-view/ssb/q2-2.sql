[sql]
SELECT
  SUM(lo_revenue),
  d_year,
  p_brand
FROM 
  lineorder,
  dates,
  part,
  supplier
WHERE 
  lo_orderdate = d_datekey
  AND lo_partkey = p_partkey
  AND lo_suppkey = s_suppkey
  AND p_brand BETWEEN 'MFGR#2221' AND 'MFGR#2228'
  AND s_region = 'ASIA'
GROUP BY 
  d_year,
  p_brand
ORDER BY 
  d_year,
  p_brand;
[result]
TOP-N (order by [[22: d_year ASC NULLS FIRST, 39: p_brand ASC NULLS FIRST]])
    TOP-N (order by [[22: d_year ASC NULLS FIRST, 39: p_brand ASC NULLS FIRST]])
        AGGREGATE ([GLOBAL] aggregate [{51: sum=sum(51: sum)}] group by [[22: d_year, 39: p_brand]] having [null]
            EXCHANGE SHUFFLE[22, 39]
                AGGREGATE ([LOCAL] aggregate [{51: sum=sum(13: lo_revenue)}] group by [[22: d_year, 39: p_brand]] having [null]
                    SCAN (columns[64: LO_REVENUE, 80: S_REGION, 85: P_BRAND, 93: d_year] predicate[80: S_REGION = ASIA AND 85: P_BRAND <= MFGR#2228 AND 85: P_BRAND >= MFGR#2221])
[end]
